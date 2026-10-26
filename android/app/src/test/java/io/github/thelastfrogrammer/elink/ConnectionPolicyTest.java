package io.github.thelastfrogrammer.elink;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConnectionPolicyTest {
    @Test public void registrationTimeoutNeverClaimsHttpWasConnected() {
        String text = PrinterErrors.describe(new java.util.concurrent.TimeoutException("PRIVATE-PIN"), "Printer registration");
        assertTrue(text.contains("no usable registration reply"));
        assertFalse(text.contains("HTTP")); assertFalse(text.contains("PRIVATE-PIN"));
        assertEquals("MQTT subscriptions timed out.", PrinterErrors.describe(new java.util.concurrent.TimeoutException(), "MQTT subscriptions"));
    }
    @Test public void retriesAreBoundedAndResetOnlyAfterConnectionSuccess() {
        ReconnectPolicy policy = new ReconnectPolicy();
        for (int delay : new int[] {1, 2, 4, 8, 16}) assertEquals(delay, policy.nextDelaySeconds());
        assertEquals(-1, policy.nextDelaySeconds()); assertEquals(-1, policy.nextDelaySeconds());
        policy.connected(); assertEquals(1, policy.nextDelaySeconds());
    }
    @Test public void authenticationErrorsDoNotCauseRetryStorms() {
        assertFalse(PrinterErrors.retryable(new PrinterErrors.Rejected(1000)));
        assertFalse(PrinterErrors.retryable(new IllegalStateException("Registration rejected")));
        assertTrue(PrinterErrors.retryable(new ConnectException("failed")));
        assertTrue(PrinterErrors.retryable(new SocketTimeoutException("failed")));
        assertFalse(PrinterErrors.retryable(new PrinterErrors.HttpStatus(404)));
        assertTrue(PrinterErrors.retryable(new PrinterErrors.HttpStatus(503)));
        assertTrue(PrinterErrors.describe(new PrinterErrors.HttpStatus(404), "HTTP").contains("HTTP 404"));
    }
    @Test public void rawCredentialBearingUrlsNeverAppearInUserErrors() {
        String secret = "do-not-leak";
        String text = PrinterErrors.describe(new ConnectException("http://192.168.1.2/system/info?X-Token=" + secret), "HTTP (port 80)");
        assertFalse(text.contains(secret)); assertFalse(text.contains("X-Token")); assertTrue(text.contains("could not reach"));
        text = PrinterErrors.describe(new RuntimeException("X-Token=" + secret), "HTTP");
        assertFalse(text.contains(secret)); assertTrue(text.contains("Check connection"));
    }
    @Test public void mqttNotAuthorizedDoesNotClaimPasswordWasNecessarilyWrong() {
        org.eclipse.paho.client.mqttv3.MqttException error = new org.eclipse.paho.client.mqttv3.MqttSecurityException(5);
        String message = PrinterErrors.describe(error, "MQTT (port 1883)");
        assertTrue(message.contains("not authorized")); assertTrue(message.contains("does not specify"));
        assertFalse(message.contains("access code rejected")); assertFalse(PrinterErrors.retryable(error));
        assertFalse(PrinterErrors.retryable(new PrinterErrors.CloudMode()));
    }
    @Test public void settingPayloadsStayExplicitAndAxisMovementRemainsBlocked() throws Exception {
        assertTrue(Cc2Codec.autoRefillRequest(8, true).getJSONObject("params").getBoolean("auto_refill"));
        assertEquals(2004, Cc2Codec.autoRefillRequest(9, false).getInt("method"));
        assertEquals(2005, Cc2Codec.request(10, Cc2Codec.CANVAS).getInt("method"));
        try { Cc2Codec.request(11, Cc2Codec.AUTO_REFILL); fail("Do not invent a missing setting"); } catch (IllegalArgumentException expected) { }
        try { Cc2Codec.request(12, 1027); fail("Axis movement is unverified"); } catch (IllegalArgumentException expected) { }
    }
}
