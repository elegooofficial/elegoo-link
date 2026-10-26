/* Derived from ELEGOO's Apache-2.0 CC2 adapter. See android/PROTOCOL.md.
 * Android port and modifications, 2026. */
package io.github.thelastfrogrammer.elink;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Locale;
import java.util.Iterator;

/** Pure protocol logic, independent of Android and MQTT transport. */
public final class Cc2Codec {
    public static final int ATTRIBUTES = 1001, STATUS = 1002, START = 1020, PAUSE = 1021, STOP = 1022, RESUME = 1023,
        TEMPERATURE = 1028, LIGHT = 1029, FAN = 1030, SPEED = 1031, HISTORY = 1036, CAMERA = 1042,
        FILES = 1044, DELETE = 1047, DISK = 1048, CANVAS = 2005, AUTO_REFILL = 2004;
    private JSONObject snapshot;
    private int sequence = -1, gaps;

    public static JSONObject request(int id, int method) throws JSONException {
        if (method != ATTRIBUTES && method != STATUS && method != PAUSE && method != STOP && method != RESUME
            && method != CANVAS && method != HISTORY && method != CAMERA && method != DISK)
            throw new IllegalArgumentException("Unverified command");
        return new JSONObject().put("id", id).put("method", method).put("params", new JSONObject());
    }
    public static JSONObject autoRefillRequest(int id, boolean enabled) throws JSONException {
        return new JSONObject().put("id", id).put("method", AUTO_REFILL).put("params", new JSONObject().put("auto_refill", enabled));
    }
    private static JSONObject envelope(int id, int method, JSONObject params) throws JSONException {
        return new JSONObject().put("id", id).put("method", method).put("params", params);
    }
    public static void storage(String value) { if (!"local".equals(value) && !"u-disk".equals(value)) throw new IllegalArgumentException("Unsupported storage"); }
    public static void filename(String value) {
        if (value == null || value.isEmpty() || value.length() > 240 || value.contains("/") || value.contains("\\")
            || value.matches("(?s).*[\\p{Cntrl}].*") || !value.toLowerCase(Locale.ROOT).endsWith(".gcode")) throw new IllegalArgumentException("Select a G-code filename from the printer");
    }
    public static JSONObject filesRequest(int id, String storage, int offset) throws JSONException {
        storage(storage); if (offset < 0) throw new IllegalArgumentException("Invalid page offset");
        JSONObject params = new JSONObject().put("storage_media", storage).put("offset", offset).put("limit", 50);
        if ("u-disk".equals(storage)) params.put("dir", "/");
        return envelope(id, FILES, params);
    }
    public static JSONObject deleteRequest(int id, String storage, String filename) throws JSONException {
        storage(storage); filename(filename);
        return envelope(id, DELETE, new JSONObject().put("storage_media", storage).put("file_path", new JSONArray().put(filename)));
    }
    public static JSONObject startRequest(int id, String storage, String filename, boolean leveling, boolean forceLeveling,
                                         boolean timelapse, String plate, JSONArray mappings) throws JSONException {
        storage(storage); filename(filename);
        if (!"A".equals(plate) && !"B".equals(plate)) throw new IllegalArgumentException("Choose plate A or B");
        if (forceLeveling && !leveling) throw new IllegalArgumentException("Forced leveling requires a printer check");
        if (mappings == null || mappings.length() > 8) throw new IllegalArgumentException("Invalid tool mappings");
        boolean[] used = new boolean[8];
        for (int i = 0; i < mappings.length(); i++) {
            JSONObject map = mappings.getJSONObject(i); int tool = map.getInt("t"), canvas = map.getInt("canvas_id"), tray = map.getInt("tray_id");
            if (tool < 0 || tool > 7 || used[tool] || canvas < 0 || tray < 0 || tray > 15) throw new IllegalArgumentException("Invalid tool mapping");
            used[tool] = true;
        }
        JSONObject config = new JSONObject().put("printer_check", leveling).put("bedlevel_force", forceLeveling)
            .put("delay_video", timelapse).put("print_layout", plate).put("slot_map", new JSONArray(mappings.toString()));
        return envelope(id, START, new JSONObject().put("filename", filename).put("storage_media", storage).put("config", config));
    }
    public static JSONObject lightRequest(int id, boolean on) throws JSONException { return envelope(id, LIGHT, new JSONObject().put("power", on ? 1 : 0)); }
    public static JSONObject temperatureRequest(int id, int nozzle, int bed) throws JSONException {
        if (nozzle < 0 || nozzle > 300 || bed < 0 || bed > 100) throw new IllegalArgumentException("Supported targets: nozzle 0–300°C, bed 0–100°C");
        return envelope(id, TEMPERATURE, new JSONObject().put("extruder", nozzle).put("heater_bed", bed));
    }
    public static JSONObject fanRequest(int id, String fan, int percent) throws JSONException {
        if (!"fan".equals(fan) && !"aux_fan".equals(fan) && !"box_fan".equals(fan) || percent < 0 || percent > 100) throw new IllegalArgumentException("Invalid fan setting");
        return envelope(id, FAN, new JSONObject().put(fan, Math.round(percent * 255f / 100)));
    }
    public static JSONObject speedRequest(int id, int mode) throws JSONException {
        if (mode < 0 || mode > 3) throw new IllegalArgumentException("Invalid speed mode");
        return envelope(id, SPEED, new JSONObject().put("mode", mode));
    }
    public static boolean isQuery(int method) { return method == FILES || method == HISTORY || method == DISK || method == CAMERA; }
    public static boolean queryShape(int method, JSONObject result) {
        if (method == FILES) return result.optJSONArray("file_list") != null;
        if (method == HISTORY) return result.optJSONArray("history_task_list") != null;
        if (method == DISK) return result.opt("total_bytes") instanceof Number && result.opt("used_bytes") instanceof Number;
        if (method == CAMERA) return result.opt("url") instanceof String;
        return false;
    }
    public static boolean changing(int method) {
        return method == START || method == PAUSE || method == STOP || method == RESUME || method == DELETE || method == AUTO_REFILL
            || method == LIGHT || method == TEMPERATURE || method == FAN || method == SPEED;
    }

    public static boolean validRegistration(JSONObject message, String clientId) {
        return clientId.equals(message.optString("client_id")) && "ok".equals(message.optString("error"));
    }

    /** Returns false when a delta has no baseline or sequence gaps require a refresh. */
    public synchronized boolean accept(JSONObject message) throws JSONException {
        int method = message.optInt("method", -1);
        JSONObject result = message.optJSONObject("result");
        if (result == null || (method != STATUS && method != 6000)) return false;
        if (method == STATUS) {
            if (result.optInt("error_code", -1) != 0) return false;
            snapshot = copy(result);
            sequence = -1;
            gaps = 0;
            return true;
        }
        if (snapshot == null) return false;
        int id = message.optInt("id", -1);
        if (id >= 0) {
            boolean continuous = sequence == -1 || id == sequence + 1 || id == 0;
            sequence = id;
            gaps = continuous ? 0 : gaps + 1;
            if (gaps >= 5) { snapshot = null; return false; }
        }
        merge(snapshot, result);
        return true;
    }

    public synchronized JSONObject snapshot() throws JSONException {
        return snapshot == null ? new JSONObject() : copy(snapshot);
    }
    /** A separate CANVAS query must update the delta baseline, so unrelated deltas cannot restore old tray data. */
    public synchronized void canvas(JSONObject canvas) throws JSONException {
        if (snapshot != null) snapshot.put("canvas_info", copy(canvas));
    }

    private static JSONObject copy(JSONObject object) throws JSONException { return new JSONObject(object.toString()); }
    private static void merge(JSONObject target, JSONObject source) throws JSONException {
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = source.get(key);
            if (!"exception_code".equals(key) && value instanceof JSONObject && target.opt(key) instanceof JSONObject)
                merge(target.getJSONObject(key), (JSONObject) value);
            else target.put(key, value);
        }
    }

    public static boolean canPause(JSONObject status) {
        JSONObject machine = status.optJSONObject("machine_status");
        return machine != null && machine.optInt("status", -1) == 2 && machine.optInt("sub_status", -1) == 2075;
    }
    public static boolean canStop(JSONObject status) {
        JSONObject machine = status.optJSONObject("machine_status");
        if (machine == null || machine.optInt("status", -1) != 2) return false;
        int sub = machine.optInt("sub_status", -1);
        return sub != 2503 && sub != 2504 && sub != 2077;
    }
    public static boolean canResume(JSONObject status) {
        JSONObject machine = status.optJSONObject("machine_status");
        return machine != null && machine.optInt("status", -1) == 2 && (machine.optInt("sub_status", -1) == 2502 || machine.optInt("sub_status", -1) == 2505);
    }
    public static boolean idle(JSONObject status) { JSONObject machine = status.optJSONObject("machine_status"); return machine != null && machine.optInt("status", -1) == 1; }
}
