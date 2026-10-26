package io.github.thelastfrogrammer.elink;

import java.io.IOException;

/** VPN presence is a routing precondition; it is not proof of the VPN's destination or provider. */
public final class VpnRouteGuard {
    private VpnRouteGuard() { }
    public interface Check { void ensure() throws IOException; }
    public static final class Unavailable extends IOException {
        public Unavailable() { super("Home VPN is unavailable or changed. Enable Tailscale / your home VPN and reconnect. Remote mode does not switch to a direct local route."); }
    }
    public static void require(boolean activeVpn, boolean sameNetwork, boolean processBound) throws Unavailable {
        if (!activeVpn || !sameNetwork || processBound) throw new Unavailable();
    }
}
