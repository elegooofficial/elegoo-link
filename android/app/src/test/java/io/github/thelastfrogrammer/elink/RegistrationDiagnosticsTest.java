package io.github.thelastfrogrammer.elink;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class RegistrationDiagnosticsTest {
    @Test public void repliesRequireActualStringFieldsAndMatchingClient() throws Exception {
        RegistrationDiagnostics facts = new RegistrationDiagnostics();
        assertFalse(facts.response(new JSONObject().put("client_id", 123).put("error", "ok"), "123"));
        assertFalse(facts.response(new JSONObject().put("client_id", "PRIVATE-CLIENT").put("error", "PRIVATE-PIN"), "expected"));
        assertTrue(facts.response(new JSONObject().put("client_id", "expected").put("error", "PRIVATE-PIN"), "expected"));
        String report = facts.report();
        assertTrue(report.contains("malformed 1, wrong client 1"));
        assertTrue(report.contains("rejected for this client"));
        assertFalse(report.contains("PRIVATE"));
    }
    @Test public void subackMustAcceptEveryRequestedTopicAndMayDowngradeQos() {
        assertTrue(new RegistrationDiagnostics().subscribed(new int[] {0,1,0}));
        for (int[] invalid : new int[][] {null, {}, {1,1}, {1,1,128}, {1,2,1}, {-1,1,1}})
            assertFalse(new RegistrationDiagnostics().subscribed(invalid));
        RegistrationDiagnostics denied = new RegistrationDiagnostics();
        assertFalse(denied.subscribed(new int[] {1,1,128}));
        assertTrue(denied.report().contains("rejected: registration replies"));
        assertTrue(denied.report().contains("Registration publish: not attempted"));
    }
    @Test public void countersAreBoundedAndBrokerAcknowledgementsNeverImplyRegistration() {
        RegistrationDiagnostics facts = new RegistrationDiagnostics();
        facts.brokerAccepted(); facts.subscribed(new int[] {1,1,1}); facts.published();
        for (int i=0;i<1100;i++) { facts.malformed(); facts.retained(); facts.status(); facts.api(); facts.other(); }
        String report = facts.report();
        assertTrue(report.contains("malformed 999")); assertTrue(report.contains("retained 999"));
        assertTrue(report.contains("status 999, API replies 999, unexpected topic 999"));
        assertTrue(report.contains("Registration reply: none"));
        assertTrue(report.contains("do not prove printer registration"));
        assertTrue(report.length() < 800);
    }
}
