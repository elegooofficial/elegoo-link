package io.github.thelastfrogrammer.elink;

import org.json.JSONObject;
import java.util.*;

/** Alerts require observed state; disconnect/idle never implies successful completion. */
public final class PrintAlerts {
    private String observedJob = "";
    private String observedName = "";
    private boolean armed;
    private Set<String> faults = new HashSet<>();
    public String update(JSONObject status) {
        String codes = StatusPresentation.faultCodes(status);
        Set<String> now = codes.isEmpty() ? new HashSet<>() : new HashSet<>(Arrays.asList(codes.split(", ")));
        Set<String> added = new HashSet<>(now); added.removeAll(faults); faults = now;
        JSONObject machine = status.optJSONObject("machine_status"), print = status.optJSONObject("print_status");
        String name = print == null ? "" : print.optString("filename"), key = print == null ? "" : print.optString("uuid", "");
        if (key.isEmpty()) key = name;
        boolean completed = false;
        if (machine != null && machine.optInt("status", -1) == 2) {
            int sub = machine.optInt("sub_status", -1);
            if (sub == 2077) { completed = armed && (key.isEmpty() || observedJob.equals(key)); armed = false; }
            else if (sub == 2503 || sub == 2504) armed = false;
            else if (!key.isEmpty()) { observedJob = key; observedName = name; armed = true; }
        } else if (machine != null && machine.optInt("status", -1) == 1) armed = false;
        if (!added.isEmpty()) return "Printer fault: " + codes;
        return completed ? "Print complete: " + StatusPresentation.clean(name.isEmpty() ? observedName : name) : "";
    }
    public void disconnected() { armed = false; }
}
