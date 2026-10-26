package io.github.thelastfrogrammer.elink;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Read-only method 7000. Accept identity only from the IP the user selected. */
public final class Cc2Discovery implements AutoCloseable {
    interface SocketFactory { DatagramSocket create() throws IOException; }
    private final SocketFactory factory;
    private final List<InetAddress> broadcasts;
    private final int port, timeoutMillis;
    private volatile DatagramSocket current;
    private volatile boolean closed;
    public static final class Info {
        public final String serial;
        public final String name, model;
        public final Boolean lanOnly, codeProtected;
        Info(String serial, String name, String model, Boolean lanOnly, Boolean codeProtected) { this.serial = serial; this.name = name; this.model = model; this.lanOnly = lanOnly; this.codeProtected = codeProtected; }
        public String summary() {
            return "Printer mode: " + (lanOnly == null ? "not reported" : lanOnly ? "LAN Only" : "cloud / WAN")
                + "; access-code protection: " + (codeProtected == null ? "not reported" : codeProtected ? "enabled" : "disabled") + ".";
        }
    }
    public Cc2Discovery() { this(DatagramSocket::new, Collections.emptyList()); }
    Cc2Discovery(SocketFactory factory, List<InetAddress> broadcasts) { this(factory, broadcasts, 52700, 4000); }
    Cc2Discovery(SocketFactory factory, List<InetAddress> broadcasts, int port, int timeoutMillis) {
        this.factory = factory; this.broadcasts = new ArrayList<>(broadcasts); this.port = port; this.timeoutMillis = timeoutMillis;
    }
    public String discover(String host) throws IOException {
        return discoverInfo(host).serial;
    }
    public Info discoverInfo(String host) throws IOException {
        if (closed) throw new IOException("Discovery cancelled");
        InetAddress target = InetAddress.getByName(host);
        byte[] request = "{\"id\":0,\"method\":7000}".getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = factory.create()) {
            current = socket;
            if (closed) throw new IOException("Discovery cancelled");
            socket.setBroadcast(true);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis), nextSend = 0;
            byte[] buffer = new byte[16384];
            while (!closed && System.nanoTime() < deadline) {
                long now = System.nanoTime();
                if (now >= nextSend) {
                    socket.send(new DatagramPacket(request, request.length, target, port));
                    for (InetAddress broadcast : broadcasts) {
                        try { socket.send(new DatagramPacket(request, request.length, broadcast, port)); }
                        catch (IOException ignored) { /* Unicast can still succeed if broadcasts are blocked. */ }
                    }
                    nextSend = now + TimeUnit.SECONDS.toNanos(1);
                }
                socket.setSoTimeout((int) Math.max(1, Math.min(500, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))));
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try { socket.receive(packet); }
                catch (SocketTimeoutException ignored) { continue; }
                if (!packet.getAddress().equals(target) || packet.getLength() == buffer.length) continue;
                Info info = parse(new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8));
                if (info != null) return info;
            }
            throw new SocketTimeoutException("No CC2 discovery identity received");
        } finally { current = null; }
    }
    static String serial(String payload) {
        Info info = parse(payload); return info == null ? null : info.serial;
    }
    static Info parse(String payload) {
        try {
            JSONObject message = new JSONObject(payload);
            if (!(message.opt("id") instanceof Number) || message.getInt("id") != 0
                || (message.has("method") && message.optInt("method", -1) != 7000)) return null;
            JSONObject result = message.optJSONObject("result");
            if (result == null || (result.has("error_code") && result.optInt("error_code", -1) != 0) || !(result.opt("sn") instanceof String)) return null;
            String serial = result.getString("sn");
            return validSerial(serial) ? new Info(serial, result.optString("host_name"), result.optString("machine_model"), flag(result.opt("lan_status")), flag(result.opt("token_status"))) : null;
        } catch (Exception ignored) { return null; }
    }
    public static final class Found {
        public final String host; public final Info info;
        Found(String host, Info info) { this.host = host; this.info = info; }
    }
    public List<Found> scan() throws IOException {
        if (closed) throw new IOException("Discovery cancelled");
        Map<String, Found> found = new LinkedHashMap<>();
        byte[] request = "{\"id\":0,\"method\":7000}".getBytes(StandardCharsets.UTF_8), buffer = new byte[16384];
        try (DatagramSocket socket = factory.create()) {
            current = socket; if (closed) throw new IOException("Discovery cancelled"); socket.setBroadcast(true); socket.setSoTimeout(500);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis), nextSend = 0;
            while (!closed && System.nanoTime() < deadline) {
                long now = System.nanoTime();
                if (now >= nextSend) {
                    for (InetAddress target : broadcasts) try { socket.send(new DatagramPacket(request, request.length, target, port)); } catch (IOException ignored) { }
                    nextSend = now + TimeUnit.SECONDS.toNanos(1);
                }
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try { socket.receive(packet); } catch (SocketTimeoutException ignored) { continue; }
                if (packet.getLength() == buffer.length || !(packet.getAddress() instanceof Inet4Address)) continue;
                String host = packet.getAddress().getHostAddress(); Info info = parse(new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8));
                if (info != null) try { new PrinterHttp(host, ""); if (found.size() < 20) found.put(host, new Found(host, info)); } catch (IllegalArgumentException ignored) { }
            }
        } finally { current = null; }
        return new ArrayList<>(found.values());
    }
    private static Boolean flag(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (number == 0) return false; if (number == 1) return true;
        }
        return null;
    }
    public static boolean validSerial(String serial) { return serial != null && serial.matches("[A-Za-z0-9_-]{1,64}"); }
    @Override public void close() { closed = true; DatagramSocket socket = current; if (socket != null) socket.close(); }
}
