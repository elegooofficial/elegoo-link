package io.github.thelastfrogrammer.elink;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.json.JSONObject;

public class PrinterIdentityTest {
    static Cc2Discovery reply(Boolean lan, Boolean codeProtected) throws Exception {
        String payload = new JSONObject().put("id", 0).put("result", new JSONObject().put("sn", "DISCOVERED").put("lan_status", lan).put("token_status", codeProtected)).toString();
        InetAddress source = InetAddress.getByName("192.168.1.84");
        return new Cc2Discovery(() -> new DatagramSocket() {
            @Override public void send(DatagramPacket packet) { assertEquals(source, packet.getAddress()); assertEquals(52700, packet.getPort()); }
            @Override public void receive(DatagramPacket packet) {
                packet.setData(payload.getBytes(StandardCharsets.UTF_8)); packet.setAddress(source); packet.setPort(52700);
            }
        }, Collections.emptyList());
    }
    private Cc2Discovery unavailable() { return new Cc2Discovery(() -> { throw new IOException("UDP blocked"); }, Collections.emptyList()); }
    @Test public void manualSerialDoesNotNeedHttpOrUdp() throws Exception {
        try (PrinterIdentity identity = new PrinterIdentity(" TEST-CC2 ", unavailable())) {
            PrinterHttp http = new PrinterHttp("192.168.1.84", "secret", url -> { fail("HTTP must not open"); return null; });
            assertEquals("TEST-CC2", identity.resolve(http)); assertEquals("secret", identity.password(http));
        }
    }
    @Test public void missingBothIdentityTransportsGivesActionableErrorWithoutSecret() throws Exception {
        try (PrinterIdentity identity = new PrinterIdentity("", unavailable())) {
            try { identity.resolve(new PrinterHttp("192.168.1.84", "secret", url -> { throw new java.net.ConnectException("http://host/?X-Token=secret"); })); fail("Expected identity error"); }
            catch (PrinterErrors.IdentityUnavailable error) {
                assertFalse(PrinterErrors.retryable(error));
                assertTrue(PrinterErrors.describe(error, "Identity").contains("optional serial field"));
                assertFalse(PrinterErrors.describe(error, "Identity").contains("secret"));
            }
        }
    }
    @Test public void disabledCodeProtectionOverridesStaleEnteredCodeWithoutLoggingIt() throws Exception {
        try (PrinterIdentity identity = new PrinterIdentity("", reply(true, false))) {
            PrinterHttp http = new PrinterHttp("192.168.1.84", "secret");
            assertEquals("DISCOVERED", identity.resolve(http)); assertEquals("123456", identity.password(http));
            assertTrue(identity.summary().contains("disabled")); assertFalse(identity.summary().contains("secret"));
        }
    }
    @Test public void enabledCodeProtectionPreservesEnteredCredential() throws Exception {
        try (PrinterIdentity identity = new PrinterIdentity("", reply(true, true))) {
            PrinterHttp http = new PrinterHttp("192.168.1.84", "secret");
            identity.resolve(http); assertEquals("secret", identity.password(http)); assertTrue(identity.summary().contains("enabled"));
        }
    }
    @Test public void absentFlagsRemainUnknownAndDoNotInventDisabledProtection() throws Exception {
        try (PrinterIdentity identity = new PrinterIdentity("", reply(null, null))) {
            PrinterHttp http = new PrinterHttp("192.168.1.84", "secret");
            identity.resolve(http); assertEquals("secret", identity.password(http)); assertTrue(identity.summary().contains("not reported"));
        }
    }
    @Test public void actualDiscoveredSerialCorrectsManualTypo() throws Exception {
        try (PrinterIdentity identity = new PrinterIdentity("TYPO", reply(true, true))) {
            assertEquals("DISCOVERED", identity.resolve(new PrinterHttp("192.168.1.84", "secret")));
            assertTrue(identity.summary().contains("Manual serial differed"));
        }
    }
}
