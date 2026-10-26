package io.github.thelastfrogrammer.elink;

import android.content.Context;
import android.net.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import javax.net.SocketFactory;

/** Explicit local sockets or the default VPN-aware route. Remote mode never selects underlying Wi-Fi. */
public final class NetworkRoute {
    public final Network network;
    private final ConnectivityManager manager;
    public final boolean vpn;
    private NetworkRoute(Network network, ConnectivityManager manager, boolean vpn) { this.network = network; this.manager = manager; this.vpn = vpn; }
    public static NetworkRoute select(Context context, boolean vpn) throws IOException { return vpn ? remote(context) : local(context); }
    public static NetworkRoute remote(Context context) throws IOException {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        NetworkRoute route = new NetworkRoute(manager.getActiveNetwork(), manager, true); route.ensure(); return route;
    }
    public void ensure() throws IOException {
        if (!vpn) return;
        Network active = manager.getActiveNetwork(); NetworkCapabilities caps = active == null ? null : manager.getNetworkCapabilities(active);
        VpnRouteGuard.require(caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN), network != null && network.equals(active), manager.getBoundNetworkForProcess() != null);
    }
    public boolean available() { try { ensure(); return true; } catch (IOException error) { return false; } }
    /** Callback only revokes a route. Existing sessions are closed by their owner; re-registration is required. */
    public AutoCloseable watch(Runnable invalid) {
        if (!vpn) return () -> { };
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            public void onAvailable(Network current) { if (!network.equals(current)) invalid.run(); }
            public void onLost(Network current) { if (network.equals(current)) invalid.run(); }
            public void onCapabilitiesChanged(Network current, NetworkCapabilities caps) { if (network.equals(current) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) invalid.run(); }
        };
        manager.registerDefaultNetworkCallback(callback);
        return () -> manager.unregisterNetworkCallback(callback);
    }
    public static NetworkRoute local(Context context) throws IOException {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities caps = manager.getNetworkCapabilities(network);
            if (caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)))
                return new NetworkRoute(network, manager, false);
        }
        throw new IOException("No local Wi-Fi network is available. Connect this phone to the printer's Wi-Fi network, then try again.");
    }
    public SocketFactory sockets() { return vpn ? new GuardedSocketFactory(this::ensure) : network.getSocketFactory(); }
    public PrinterHttp.ConnectionFactory http() { return url -> { ensure(); return (HttpURLConnection) (vpn ? url.openConnection(java.net.Proxy.NO_PROXY) : network.openConnection(url)); }; }
    public Cc2Discovery discovery() {
        if (vpn) return new Cc2Discovery(() -> { ensure(); return new DatagramSocket() {
            @Override public void send(DatagramPacket packet) throws IOException { ensure(); super.send(packet); }
        }; }, Collections.emptyList());
        List<InetAddress> broadcasts = new ArrayList<>();
        LinkProperties props = manager.getLinkProperties(network);
        if (props != null) for (LinkAddress address : props.getLinkAddresses()) {
            if (!(address.getAddress() instanceof Inet4Address) || address.getPrefixLength() >= 31) continue;
            byte[] bytes = address.getAddress().getAddress();
            int ip = 0; for (byte value : bytes) ip = (ip << 8) | (value & 255);
            int mask = address.getPrefixLength() == 0 ? 0 : -1 << (32 - address.getPrefixLength());
            int broadcast = ip | ~mask;
            try { broadcasts.add(InetAddress.getByAddress(new byte[] {(byte)(broadcast >>> 24), (byte)(broadcast >>> 16), (byte)(broadcast >>> 8), (byte)broadcast})); }
            catch (UnknownHostException ignored) { }
        }
        return new Cc2Discovery(() -> {
            DatagramSocket socket = new DatagramSocket();
            try { network.bindSocket(socket); return socket; }
            catch (IOException error) { socket.close(); throw error; }
        }, broadcasts);
    }
    public String details() {
        StringBuilder text = new StringBuilder(vpn ? "Printer traffic: remote VPN / Android default route\n" : "Local printer traffic: Wi-Fi / Ethernet\n");
        if (vpn) text.append("VPN active for this app. Verify the home gateway advertises this printer IP; VPN presence alone does not verify the route or encryption settings.\n");
        LinkProperties props = manager.getLinkProperties(network);
        if (props != null) for (LinkAddress address : props.getLinkAddresses()) {
            if (address.getAddress() instanceof Inet4Address) text.append("Phone IP: ").append(address.getAddress().getHostAddress()).append('/').append(address.getPrefixLength()).append('\n');
        }
        Network active = manager.getActiveNetwork(); NetworkCapabilities caps = active == null ? null : manager.getNetworkCapabilities(active);
        if (!vpn && caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) text.append("VPN detected. Printer traffic uses the local network directly; VPN policy can still block it.\n");
        return text.toString();
    }
    public String check(String host, String code, String manualSerial) {
        return check(host, code, manualSerial, false);
    }
    public String check(String host, String code, String manualSerial, boolean pinProbe) {
        PrinterHttp http = new PrinterHttp(host, pinProbe ? "" : code, http());
        StringBuilder text = new StringBuilder(details()).append("Printer IP: ").append(host).append("\n\n");
        if (pinProbe) text.append("Cloud-mode local PIN probe selected: read-only, no automatic retries. Check connection does not authenticate MQTT or use the PIN. Connect tests the entered PIN once.\n\n");
        boolean web = probe(host, 80, text), mqtt = probe(host, 1883, text);
        if (vpn) probe(host, 8080, text);
        boolean identity = !manualSerial.isEmpty();
        Cc2Discovery.Info info = null;
        try (Cc2Discovery discovery = discovery()) {
            info = discovery.discoverInfo(host); identity = true;
            text.append("UDP 52700 discovery: printer identity received.\n").append(info.summary()).append('\n');
            if (!manualSerial.isEmpty() && !manualSerial.equals(info.serial)) text.append("Manual serial differs from discovery. Connect will use the discovered serial.\n");
        } catch (IOException error) {
            text.append("UDP 52700 discovery: no printer identity received; LAN mode and code protection unknown.\n");
            if (identity) text.append("MQTT identity: manual serial supplied; Connect verifies registration.\n");
        }
        if (web && !pinProbe) {
            try {
                PrinterHttp effective = info != null && Boolean.FALSE.equals(info.codeProtected) ? new PrinterHttp(host, "", http()) : http;
                try { effective.systemInfo(); } finally { effective.cancel(); }
                identity = true; text.append("HTTP system info: accepted; printer identity returned.\n");
            }
            catch (Exception error) { text.append("HTTP system info: ").append(PrinterErrors.describe(error, "HTTP authentication")).append('\n'); }
        }
        if (pinProbe) {
            text.append("\nHTTP identity/authentication was not attempted; PINs are not HTTP tokens and uploads are disabled in this probe.");
            if (info != null && Boolean.TRUE.equals(info.lanOnly)) text.append("\nPrinter reports LAN Only. PIN probe expects cloud mode; choose LAN access code for the current printer setting, or leave cloud mode enabled to test Matrix coexistence.");
            else text.append("\nKeep Matrix/cloud mode enabled. TCP reachability alone does not prove PIN authentication, registration or coexistence. Firmware may reject local PIN access.");
            if (!identity) text.append("\nEnter the exact Serial Number from Settings → Device if UDP identity does not reply; PIN probe never falls back to HTTP identity.");
        }
        else if (info != null && Boolean.FALSE.equals(info.lanOnly)) text.append("\nThe printer reports cloud / WAN mode, but LAN access code was selected. To preserve Matrix, use the separate experimental read-only PIN probe. LAN Only is optional for LAN authentication.");
        else if (!web && !mqtt) text.append("\nNeither HTTP nor MQTT is reachable. Confirm the current printer IP and LAN Only. " + (vpn ? "Check the Pi/home gateway, approved printer route and VPN access rules." : "Use the same Wi-Fi and ensure the router does not isolate guest devices."));
        else if (!mqtt) text.append("\nHTTP is reachable but MQTT is not. Confirm LAN Only is enabled and local port 1883 is not blocked.");
        else if (!web) text.append(identity ? "\nMQTT is reachable and identity is available. Connect can use MQTT without HTTP. HTTP file uploads remain unavailable." : "\nMQTT is reachable. Enter the exact Serial Number from Settings → Device in the optional serial field to connect without HTTP or UDP discovery. HTTP file uploads remain unavailable.");
        else text.append("\nBoth ports are reachable. TCP checks alone do not prove MQTT authentication or client registration; Connect tests those next.");
        if (vpn) text.append("\nRemote discovery uses selected-IP unicast only; Wi-Fi broadcast scanning is unavailable. A manual serial provides identity if discovery times out. Connection route does not change the printer's authentication mode. HTTP or MQTT rejected at home will still be rejected remotely.");
        http.cancel(); return text.toString();
    }
    private boolean probe(String host, int port, StringBuilder result) {
        try (Socket socket = sockets().createSocket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            result.append("TCP ").append(port).append(port == 80 ? " (HTTP)" : port == 1883 ? " (MQTT)" : " (camera)").append(": reachable\n"); return true;
        } catch (Exception error) {
            result.append("TCP ").append(port).append(": ").append(error instanceof SocketTimeoutException ? "timed out" : error instanceof ConnectException ? "refused or unreachable" : "unreachable").append('\n'); return false;
        }
    }
}
