package io.github.thelastfrogrammer.elink;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Per-IP encrypted credentials; compatible with the previous single-printer store. */
public final class CredentialStore {
    private static final String ALIAS = "link-workshop-lan-v1";
    private final SharedPreferences prefs;
    public CredentialStore(Context context) {
        prefs = context.getSharedPreferences("printer-profile", Context.MODE_PRIVATE);
        String host = host();
        if (!host.isEmpty() && prefs.contains("remember")) {
            SharedPreferences.Editor edit = prefs.edit();
            if (!prefs.contains("remember:" + host)) {
                edit.putBoolean("remember:" + host, prefs.getBoolean("remember", false));
                if (prefs.contains("cipher")) edit.putString("cipher:" + host, prefs.getString("cipher", ""));
                if (prefs.contains("iv")) edit.putString("iv:" + host, prefs.getString("iv", ""));
            }
            edit.remove("remember").remove("cipher").remove("iv").apply();
        }
    }
    public String host() { return prefs.getString("host", ""); }
    public boolean remembers() { return remembers(host()); }
    public boolean remembers(String host) { return prefs.getBoolean("remember:" + host, false); }
    public String load() throws Exception { return load(host()); }
    public String load(String host) throws Exception {
        if (!remembers(host)) return "";
        String stored = prefs.getString("cipher:" + host, ""), iv = prefs.getString("iv:" + host, "");
        if (stored.isEmpty() || iv.isEmpty()) throw new IllegalStateException("Saved access code is unavailable");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
        cipher.updateAAD(host.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.decode(stored, Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    public void save(String host, String code, boolean remember) throws Exception {
        // Also validates the address and code before they are stored.
        new PrinterHttp(host, code);
        SharedPreferences.Editor edit = prefs.edit().putString("host", host).putBoolean("remember:" + host, remember);
        if (remember) {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key(true));
            cipher.updateAAD(host.getBytes(StandardCharsets.UTF_8));
            edit.putString("cipher:" + host, Base64.encodeToString(cipher.doFinal(code.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP));
            edit.putString("iv:" + host, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
        } else edit.remove("cipher:" + host).remove("iv:" + host);
        if (!edit.commit()) throw new IllegalStateException("Could not save printer profile");
    }
    public void forget() { forget(host()); }
    public void forget(String host) { prefs.edit().remove("cipher:" + host).remove("iv:" + host).putBoolean("remember:" + host, false).apply(); }
    private SecretKey key(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        if (!create) throw new IllegalStateException("Saved access code key is unavailable");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return generator.generateKey();
    }
}
