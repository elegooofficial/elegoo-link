package io.github.thelastfrogrammer.elink;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.*;

/** Uses Android's default VPN-aware routing; never binds a remote socket to underlying Wi-Fi. */
public final class GuardedSocketFactory extends SocketFactory {
    private final VpnRouteGuard.Check guard;
    public GuardedSocketFactory(VpnRouteGuard.Check guard) { this.guard = guard; }
    @Override public Socket createSocket() throws IOException {
        guard.ensure();
        return new Socket() {
            @Override public void connect(SocketAddress endpoint, int timeout) throws IOException { guard.ensure(); super.connect(endpoint, timeout); }
        };
    }
    private Socket connected(SocketAddress remote, SocketAddress local) throws IOException {
        Socket socket = createSocket();
        try { if (local != null) socket.bind(local); socket.connect(remote); return socket; }
        catch (IOException | RuntimeException error) { socket.close(); throw error; }
    }
    @Override public Socket createSocket(String host, int port) throws IOException { guard.ensure(); return connected(new InetSocketAddress(host,port),null); }
    @Override public Socket createSocket(InetAddress host, int port) throws IOException { return connected(new InetSocketAddress(host,port),null); }
    @Override public Socket createSocket(String host,int port,InetAddress local,int localPort) throws IOException { guard.ensure(); return connected(new InetSocketAddress(host,port),new InetSocketAddress(local,localPort)); }
    @Override public Socket createSocket(InetAddress host,int port,InetAddress local,int localPort) throws IOException { return connected(new InetSocketAddress(host,port),new InetSocketAddress(local,localPort)); }
}
