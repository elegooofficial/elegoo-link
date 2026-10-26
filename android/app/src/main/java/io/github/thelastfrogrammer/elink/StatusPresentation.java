package io.github.thelastfrogrammer.elink;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Read-only presentation of actual reported fields; unknown states and fault codes remain explicit. */
public final class StatusPresentation {
    private StatusPresentation() { }
    public static String state(JSONObject status) {
        JSONObject machine = status.optJSONObject("machine_status");
        if (machine == null) return "Waiting for printer";
        int state = machine.optInt("status", -1), sub = machine.optInt("sub_status", -1);
        if (state == 1) return "Idle";
        if (state == 0) return "Initializing";
        if (state != 2) return "Machine state " + state;
        switch (sub) {
            case 2075: return "Printing";
            case 2501: return "Pausing";
            case 2502: case 2505: return "Paused";
            case 2503: return "Stopping";
            case 2504: return "Stopped";
            case 2077: return "Print complete";
            default: return "Print preparation · " + sub;
        }
    }
    public static String faultCodes(JSONObject status) {
        JSONObject exception = status.optJSONObject("exception");
        JSONObject codes = exception == null ? status.optJSONObject("exception_code") : exception.optJSONObject("exception_code");
        if (codes == null || codes.length() == 0) return "";
        List<String> keys = new ArrayList<>(); Iterator<String> it = codes.keys();
        while (it.hasNext()) keys.add(it.next()); Collections.sort(keys);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(keys.size(), 24); i++) { if (i > 0) text.append(", "); text.append(clean(keys.get(i))); }
        if (keys.size() > 24) text.append(" …");
        return text.toString();
    }
    public static String canvas(JSONObject canvas) {
        if (canvas == null) return "CANVAS information has not been reported.";
        StringBuilder text = new StringBuilder("Automatic refill: ");
        text.append(canvas.has("auto_refill") ? (canvas.optBoolean("auto_refill") ? "On" : "Off") : "Not reported");
        JSONArray list = canvas.optJSONArray("canvas_list");
        if (list == null || list.length() == 0) return text.append("\nNo CANVAS trays reported.").toString();
        for (int i = 0; i < Math.min(list.length(), 8); i++) {
            JSONObject unit = list.optJSONObject(i); if (unit == null) continue;
            int id = unit.optInt("canvas_id", -1);
            Object connected = unit.opt("connected");
            text.append("\n\nCANVAS ").append(id).append(connected == null ? " · Connection not reported" : (Boolean.TRUE.equals(connected) || unit.optInt("connected", 0) == 1) ? " · Connected" : " · Not connected");
            JSONArray trays = unit.optJSONArray("tray_list"); if (trays == null) continue;
            for (int t = 0; t < Math.min(trays.length(), 16); t++) {
                JSONObject tray = trays.optJSONObject(t); if (tray == null) continue;
                int trayId = tray.optInt("tray_id", -1);
                boolean active = canvas.has("active_canvas_id") && canvas.has("active_tray_id")
                    && id == canvas.optInt("active_canvas_id") && trayId == canvas.optInt("active_tray_id");
                text.append("\n").append(active ? "● " : "○ ").append("Tray ").append(trayId).append(active ? " · Active" : "");
                text.append(" · ").append(clean(tray.optString("filament_type", "Material not reported")));
                String name = clean(tray.optString("filament_name")), color = clean(tray.optString("filament_color"));
                if (!name.isEmpty()) text.append(" · ").append(name);
                if (!color.isEmpty()) text.append(" · ").append(color);
                if (tray.has("status")) text.append(" · State ").append(tray.optInt("status"));
                if (tray.has("min_nozzle_temp") && tray.has("max_nozzle_temp"))
                    text.append("\n    Nozzle ").append(tray.optInt("min_nozzle_temp")).append("–").append(tray.optInt("max_nozzle_temp")).append("°C");
            }
        }
        return text.toString();
    }
    public static String clean(String value) { return value.replaceAll("[\\p{Cntrl}]", " ").substring(0, Math.min(value.length(), 160)); }
}
