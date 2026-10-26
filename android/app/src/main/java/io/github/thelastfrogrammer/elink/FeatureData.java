package io.github.thelastfrogrammer.elink;

import org.json.*;
import java.net.URI;
import java.util.Locale;

/** Presentation and validation of CC2 file/query data. No wire-field names are guessed. */
public final class FeatureData {
    private FeatureData() { }
    public static String size(long value) {
        if (value < 0) return "Not reported";
        if (value < 1024) return value + " B";
        if (value < 1048576) return String.format(Locale.ROOT, "%.1f KiB", value / 1024.0);
        return String.format(Locale.ROOT, "%.1f MiB", value / 1048576.0);
    }
    public static String file(JSONObject file) {
        StringBuilder text = new StringBuilder(StatusPresentation.clean(file.optString("filename", "Unnamed file")));
        if (file.has("size")) text.append("\n").append(size(file.optLong("size", -1)));
        if (file.has("layer")) text.append(" · ").append(file.optInt("layer")).append(" layers");
        if (file.has("print_time")) text.append("\nEstimated print time: ").append(duration(file.optLong("print_time", -1)));
        if (file.has("total_filament_used")) text.append("\nFilament used (reported): ").append(StatusPresentation.clean(file.opt("total_filament_used").toString()));
        if (file.has("color_map")) text.append("\nSliced filament information: ").append(StatusPresentation.clean(file.opt("color_map").toString()));
        return text.toString();
    }
    public static String duration(long seconds) { return seconds < 0 ? "Not reported" : seconds / 3600 + "h " + seconds % 3600 / 60 + "m"; }
    public static String history(JSONObject result) {
        JSONArray rows = result.optJSONArray("history_task_list");
        if (rows == null) return "Print history has not been reported.";
        if (rows.length() == 0) return "No print history reported.";
        StringBuilder text = new StringBuilder();
        for (int i = rows.length() - 1; i >= Math.max(0, rows.length() - 50); i--) {
            JSONObject row = rows.optJSONObject(i); if (row == null) continue;
            if (text.length() > 0) text.append("\n\n");
            int state = row.optInt("task_status", -1);
            text.append(StatusPresentation.clean(row.optString("task_name", "Unnamed job"))).append("\n")
                .append(state == 1 ? "Completed" : state == 2 ? "Cancelled" : "Reported state " + state);
            if (row.has("begin_time") && row.has("end_time")) {
                long elapsed = row.optLong("end_time") - row.optLong("begin_time");
                if (elapsed >= 0) text.append(" · ").append(duration(elapsed));
            }
        }
        return text.toString();
    }
    public static String disk(JSONObject data) { return "Internal storage: " + size(data.optLong("used_bytes", -1)) + " used / " + size(data.optLong("total_bytes", -1)); }
    public static String cameraUrl(String host, String supplied) throws Exception {
        new PrinterHttp(host, "");
        URI uri = new URI(supplied);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
            || !host.equals(uri.getHost()) || uri.getUserInfo() != null || uri.getFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535)
            throw new IllegalArgumentException("Use a camera URL on the selected printer's IP");
        return uri.toASCIIString();
    }
    public static boolean mappings(JSONObject canvas, JSONArray maps) {
        if (maps.length() == 0) return true;
        if (canvas == null || canvas.optJSONArray("canvas_list") == null) return false;
        JSONArray units = canvas.optJSONArray("canvas_list");
        for (int i = 0; i < maps.length(); i++) {
            JSONObject map = maps.optJSONObject(i); if (map == null) return false;
            boolean found = false;
            for (int u = 0; u < units.length(); u++) {
                JSONObject unit = units.optJSONObject(u); if (unit == null || unit.optInt("canvas_id", -1) != map.optInt("canvas_id", -2)
                    || !(Boolean.TRUE.equals(unit.opt("connected")) || unit.optInt("connected", 0) == 1)) continue;
                JSONArray trays = unit.optJSONArray("tray_list"); if (trays == null) continue;
                for (int t = 0; t < trays.length(); t++) {
                    JSONObject tray = trays.optJSONObject(t);
                    if (tray != null && tray.optInt("tray_id", -1) == map.optInt("tray_id", -2) && !tray.optString("filament_type").isEmpty()) found = true;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}
