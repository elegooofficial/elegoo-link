package io.github.thelastfrogrammer.elink;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.paho.client.mqttv3.MqttException;

public class VpnRouteTest {
    @Test public void onlyAnUnboundUnchangedActiveVpnPasses() throws Exception {
        for (int bits = 0; bits < 8; bits++) {
            boolean active = (bits & 1) != 0, same = (bits & 2) != 0, bound = (bits & 4) != 0;
            try { VpnRouteGuard.require(active, same, bound); assertEquals(3, bits); }
            catch (VpnRouteGuard.Unavailable error) { assertNotEquals(3, bits); }
        }
    }
    @Test public void creationRefusesUnavailableVpn() throws Exception {
        GuardedSocketFactory factory = new GuardedSocketFactory(() -> { throw new VpnRouteGuard.Unavailable(); });
        try { factory.createSocket(); fail("Expected route refusal"); } catch (VpnRouteGuard.Unavailable expected) { }
    }
    private void lossBeforeConnect(boolean timed) throws Exception {
        AtomicBoolean active = new AtomicBoolean(true);
        GuardedSocketFactory factory = new GuardedSocketFactory(() -> VpnRouteGuard.require(active.get(), true, false));
        try (Socket socket = factory.createSocket(); ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            active.set(false);
            try {
                InetSocketAddress destination = new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort());
                if (timed) socket.connect(destination, 1000); else socket.connect(destination);
                fail("A socket made before VPN loss must still refuse connection");
            } catch (VpnRouteGuard.Unavailable expected) { }
            assertFalse(socket.isConnected());
            server.setSoTimeout(100);
            try (Socket ignored = server.accept()) { fail("No direct connection may arrive"); }
            catch (SocketTimeoutException expected) { }
        }
    }
    @Test public void timedConnectRechecksTheVpn() throws Exception { lossBeforeConnect(true); }
    @Test public void ordinaryConnectRechecksTheVpn() throws Exception { lossBeforeConnect(false); }
    @Test public void allConnectedFactoryOverloadsUseTheGuard() throws Exception {
        AtomicBoolean active = new AtomicBoolean(true);
        InetAddress loop = InetAddress.getLoopbackAddress();
        GuardedSocketFactory factory = new GuardedSocketFactory(() -> VpnRouteGuard.require(active.get(), true, false));
        try (ServerSocket server = new ServerSocket(0, 4, loop)) {
            for (int variant = 0; variant < 4; variant++) {
                try (Socket client = connect(factory, variant, loop, server.getLocalPort()); Socket accepted = server.accept()) { assertTrue(client.isConnected()); }
            }
            active.set(false);
            for (int variant = 0; variant < 4; variant++) {
                try (Socket ignored = connect(factory, variant, loop, server.getLocalPort())) { fail("Expected route refusal"); }
                catch (VpnRouteGuard.Unavailable expected) { }
            }
        }
    }
    private Socket connect(GuardedSocketFactory factory, int variant, InetAddress loop, int port) throws IOException {
        if (variant == 0) return factory.createSocket(loop.getHostAddress(), port);
        if (variant == 1) return factory.createSocket(loop, port);
        if (variant == 2) return factory.createSocket(loop.getHostAddress(), port, loop, 0);
        return factory.createSocket(loop, port, loop, 0);
    }
    @Test public void wrappedMqttFailureKeepsVpnAdviceWithoutRawExceptions() {
        IOException secret = new IOException("http://printer/?X-Token=secret", new VpnRouteGuard.Unavailable());
        String text = PrinterErrors.describe(new MqttException(secret), "MQTT");
        assertTrue(text.contains("Home VPN")); assertFalse(text.contains("secret")); assertFalse(text.contains("http://"));
    }
    @Test public void discoveryVpnLossDoesNotFallBackToManualIdentityOrHttp() throws Exception {
        for (String serial : new String[] {"", "TEST-CC2"}) {
            Cc2Discovery discovery = new Cc2Discovery(() -> { throw new VpnRouteGuard.Unavailable(); }, Collections.emptyList());
            try (PrinterIdentity identity = new PrinterIdentity(serial, discovery)) {
                PrinterHttp http = new PrinterHttp("192.168.1.84", "secret", url -> { fail("HTTP must not open after VPN loss"); return null; });
                try { identity.resolve(http); fail("Expected VPN failure"); } catch (VpnRouteGuard.Unavailable expected) { }
            }
        }
    }
    @Test public void httpVpnLossRetainsRetryableRouteError() throws Exception {
        Cc2Discovery discovery = new Cc2Discovery(() -> { throw new IOException("UDP blocked"); }, Collections.emptyList());
        try (PrinterIdentity identity = new PrinterIdentity("", discovery)) {
            PrinterHttp http = new PrinterHttp("192.168.1.84", "secret", url -> { throw new VpnRouteGuard.Unavailable(); });
            try { identity.resolve(http); fail("Expected VPN failure"); }
            catch (VpnRouteGuard.Unavailable expected) { assertTrue(PrinterErrors.retryable(expected)); }
        }
    }
}
