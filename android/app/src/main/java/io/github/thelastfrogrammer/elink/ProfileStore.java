package io.github.thelastfrogrammer.elink;

import android.content.*;
import org.json.*;

/** Names, IPs, serials and route/authentication choices only. Pairing PINs are never stored. Secrets live exclusively in CredentialStore. */
public final class ProfileStore {
    private final SharedPreferences prefs;
    public ProfileStore(Context context) { prefs = context.getSharedPreferences("workshop-settings", Context.MODE_PRIVATE); }
    public JSONArray all() { try { return new JSONArray(prefs.getString("profiles", "[]")); } catch (Exception error) { return new JSONArray(); } }
    public JSONObject find(String host) {
        JSONArray rows = all(); for (int i = 0; i < rows.length(); i++) { JSONObject row = rows.optJSONObject(i); if (row != null && host.equals(row.optString("host"))) return row; } return new JSONObject();
    }
    public void save(String name, String host, String serial) throws Exception { save(name, host, serial, false); }
    public void save(String name, String host, String serial, boolean remote) throws Exception { save(name, host, serial, remote, false); }
    public void save(String name, String host, String serial, boolean remote, boolean pinProbe) throws Exception {
        new PrinterHttp(host, ""); if (!serial.isEmpty() && !Cc2Discovery.validSerial(serial)) throw new IllegalArgumentException("Invalid serial");
        JSONArray rows = all(), next = new JSONArray();
        for (int i = 0; i < rows.length(); i++) { JSONObject row = rows.optJSONObject(i); if (row != null && !host.equals(row.optString("host"))) next.put(row); }
        if (next.length() >= 20) throw new IllegalStateException("Twenty printer profiles supported");
        next.put(new JSONObject().put("name", StatusPresentation.clean(name.isEmpty() ? host : name)).put("host", host).put("serial", serial).put("remote_vpn", remote).put("pin_probe", pinProbe));
        if (!prefs.edit().putString("profiles", next.toString()).commit()) throw new IllegalStateException("Cannot save profile");
    }
    public void remove(String host) {
        JSONArray rows = all(), next = new JSONArray(); for (int i = 0; i < rows.length(); i++) { JSONObject row = rows.optJSONObject(i); if (row != null && !host.equals(row.optString("host"))) next.put(row); }
        prefs.edit().putString("profiles", next.toString()).apply();
    }
}
