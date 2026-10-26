package io.github.thelastfrogrammer.elink;

import org.json.JSONObject;

/** Bounded, in-memory registration facts. Never retain payloads, topics, identities or credentials. */
final class RegistrationDiagnostics {
    private boolean broker, subscribed, sent;
    private String subscription = "not attempted", reply = "none";
    private int malformed, unmatched, retained, status, api, other;
    synchronized void brokerAccepted() { broker = true; }
    synchronized void subscribing() { subscription = "awaiting SUBACK"; }
    synchronized boolean subscribed(int[] granted) {
        if (granted == null || granted.length != 3) { subscription = "incomplete SUBACK"; return false; }
        String[] labels = {"API replies", "status", "registration replies"};
        for (int i = 0; i < granted.length; i++) {
            if (granted[i] == 128) { subscription = "rejected: " + labels[i]; return false; }
            if (granted[i] < 0 || granted[i] > 1) { subscription = "invalid SUBACK"; return false; }
        }
        subscribed = true; subscription = "3/3 accepted (SUBACK)"; return true;
    }
    synchronized void published() { sent = true; }
    synchronized void malformed() { malformed = increment(malformed); }
    synchronized void retained() { retained = increment(retained); }
    synchronized void status() { status = increment(status); }
    synchronized void api() { api = increment(api); }
    synchronized void other() { other = increment(other); }
    synchronized boolean response(JSONObject payload, String expectedClient) {
        Object client = payload.opt("client_id"), error = payload.opt("error");
        if (!(client instanceof String) || !(error instanceof String)) { malformed(); return false; }
        if (!expectedClient.equals(client)) { unmatched = increment(unmatched); return false; }
        reply = "ok".equals(error) ? "accepted for this client" : "rejected for this client";
        return true;
    }
    private static int increment(int value) { return Math.min(999, value + 1); }
    synchronized String report() {
        return "MQTT broker: " + (broker ? "CONNECT accepted" : "CONNECT not completed")
            + "\nSubscriptions: " + subscription
            + "\nRegistration publish: " + (sent ? "broker acknowledged (QoS 1 / PUBACK)" : subscribed ? "PUBACK not completed" : "not attempted")
            + "\nRegistration reply: " + reply
            + "\nIgnored registration replies: malformed " + malformed + ", wrong client " + unmatched + ", retained " + retained
            + "\nOther events before registration: status " + status + ", API replies " + api + ", unexpected topic " + other
            + "\nBroker acknowledgements do not prove printer registration or Matrix coexistence. Counts stop at 999; no payloads or credentials are included.";
    }
}
