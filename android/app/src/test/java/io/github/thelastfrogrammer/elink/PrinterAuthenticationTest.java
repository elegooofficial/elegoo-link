package io.github.thelastfrogrammer.elink;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.Collections;

public class PrinterAuthenticationTest {
    private Cc2Discovery unavailable() { return new Cc2Discovery(() -> { throw new IOException("UDP blocked"); }, Collections.emptyList()); }
    @Test public void pinNeverDefaultsOrUsesLanCodeProtectionFlags() throws Exception {
        PrinterAuthentication auth = new PrinterAuthentication(true, "CURRENT-PIN");
        for (Boolean protection : new Boolean[] {true, false, null}) {
            try (Cc2Discovery discovery = PrinterIdentityTest.reply(false, protection)) {
                assertEquals("CURRENT-PIN", auth.password(discovery.discoverInfo("192.168.1.84"), "LAN-CODE"));
            }
        }
        assertEquals("CURRENT-PIN", auth.password(null, "123456"));
    }
    @Test public void pinRequiresExplicitNonemptyInputAndRejectsControlCharacters() {
        for (String value : new String[] {null, "", " ", "abc\rdef", "abc\ndef", "abc\0def", new String(new char[257]).replace('\0', 'x')}) {
            try { new PrinterAuthentication(true, value); fail("Invalid PIN must be refused"); }
            catch (IllegalArgumentException expected) { assertFalse(expected.getMessage().contains("abc")); }
        }
    }
    @Test public void knownLanOnlyRefusesPinInsteadOfTryingAnotherCredential() throws Exception {
        try (Cc2Discovery discovery = PrinterIdentityTest.reply(true, true)) {
            try { new PrinterAuthentication(true, "CURRENT-PIN").password(discovery.discoverInfo("192.168.1.84"), "LAN-CODE"); fail("Mode mismatch"); }
            catch (PrinterErrors.ModeMismatch expected) { assertFalse(PrinterErrors.retryable(expected)); }
        }
    }
    @Test public void lanIntentStillRefusesKnownCloudMode() throws Exception {
        try (Cc2Discovery discovery = PrinterIdentityTest.reply(false, false)) {
            try { new PrinterAuthentication(false, "").password(discovery.discoverInfo("192.168.1.84"), "LAN-CODE"); fail("LAN code must not become a PIN"); }
            catch (PrinterErrors.CloudMode expected) { assertTrue(PrinterErrors.describe(expected, "MQTT").contains("PIN probe")); }
        }
    }
    @Test public void lanProtectionOffStillUsesTheDocumentedDefault() throws Exception {
        try (Cc2Discovery discovery = PrinterIdentityTest.reply(true, false)) {
            assertEquals("123456", new PrinterAuthentication(false, "").password(discovery.discoverInfo("192.168.1.84"), "STALE-CODE"));
        }
    }
    @Test public void missingPinIdentityNeverOpensHttp() throws Exception {
        try (PrinterIdentity identity = new PrinterIdentity("", unavailable(), new PrinterAuthentication(true, "CURRENT-PIN"))) {
            try { identity.resolve(new PrinterHttp("192.168.1.84", "", url -> { fail("PIN identity must not use HTTP"); return null; })); fail("Missing identity"); }
            catch (PrinterErrors.IdentityUnavailable expected) { assertFalse(PrinterErrors.retryable(expected)); }
        }
    }
    @Test public void manualSerialAllowsExplicitPinWithUnknownModeAndNoHttp() throws Exception {
        try (PrinterIdentity identity = new PrinterIdentity("MANUAL-CC2", unavailable(), new PrinterAuthentication(true, "CURRENT-PIN"))) {
            PrinterHttp http = new PrinterHttp("192.168.1.84", "", url -> { fail("HTTP must not open"); return null; });
            assertEquals("MANUAL-CC2", identity.resolve(http)); assertEquals("CURRENT-PIN", identity.password(http)); assertTrue(identity.readOnly());
            assertTrue(identity.summary().contains("unverified")); assertFalse(identity.summary().contains("CURRENT-PIN"));
        }
    }
    @Test public void authoritativeDiscoverySerialStillWinsInPinProbe() throws Exception {
        try (PrinterIdentity identity = new PrinterIdentity("TYPO", PrinterIdentityTest.reply(false, true), new PrinterAuthentication(true, "CURRENT-PIN"))) {
            assertEquals("DISCOVERED", identity.resolve(new PrinterHttp("192.168.1.84", "")));
            assertTrue(identity.summary().contains("differed")); assertFalse(identity.summary().contains("CURRENT-PIN"));
        }
    }
}
