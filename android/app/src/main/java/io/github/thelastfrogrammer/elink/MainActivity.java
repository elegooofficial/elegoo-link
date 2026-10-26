package io.github.thelastfrogrammer.elink;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.Bitmap;
import android.content.res.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.util.Locale;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int PICK_FILE = 1, PICK_SNAPSHOT = 3, SAVE_GCODE = 4;
    private String pendingExportHash = "";
    private android.net.Uri pendingExportUri;
    private TextView inspection;
    private ImageView filePreview;
    private TextView previewInfo;
    private GcodeInspector.Report renderedReport;
    private Button materialDetails;
    private Button saveCopy, shareInspection, clearCopy, cancelDownload;
    private int INK = 0xff142c3b, MUTED = 0xff536976, TEAL = 0xff006b65, BACKGROUND = 0xffedf3f4, SURFACE = Color.WHITE, BUTTON = 0xffe0efec;
    private boolean dark;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService checks = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private LinearLayout currentSection, fileRows;
    private final LinearLayout[] pages = new LinearLayout[4];
    private final Button[] tabs = new Button[4];
    private int page = 3;
    private SharedPreferences settings;
    private ProfileStore profiles;
    private EditText profileName, cameraAddress, pairingPin;
    private TextView summary, fileInfo, historyInfo, diskInfo, cameraInfo;
    private Spinner storagePicker, routePicker, authPicker;
    private NetworkRoute cameraRoute;
    private AutoCloseable cameraRouteWatch;
    private Button resume, discover, saveProfile, chooseProfile, removeProfile, cancelUpload, listFiles, previousFiles, nextFiles, loadHistory, loadDisk,
        cameraStart, cameraQuery, cameraSnapshot, heater, fan, speed, lightOn, lightOff;
    private ImageView cameraImage;
    private JSONObject renderedFiles;
    private Cc2Discovery scanning;
    private MjpegPlayer cameraPlayer;
    private Bitmap lastFrame, pendingSnapshot;
    private String cameraHost = "";
    private String cameraReported = "";
    private boolean scanningNow;
    private EditText host, access, serial;
    private CheckBox remember;
    private TextView connection, state, temperatures, job, feedback, selected, faults, trays, identity, diagnostics;
    private Button connect, pause, stop, refresh, upload, pick, refill, check, forget;
    private ProgressBar progress;
    private CredentialStore credentials;
    private PrinterService printer;
    private JSONObject snapshot = new JSONObject();
    private boolean active, bound, checking;
    private String pendingFeedback;
    private final ServiceConnection binding = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            printer = ((PrinterService.LocalBinder) binder).service();
            if (pendingFeedback != null) { printer.feedback = pendingFeedback; pendingFeedback = null; }
            finishPendingExport();
            if (printer.connecting()) { routePicker.setSelection(printer.remote() ? 1 : 0); authPicker.setSelection(printer.pinProbe() ? 1 : 0); host.setText(printer.host()); if (printer.pinProbe()) pairingPin.setText(printer.accessCode()); else access.setText(printer.accessCode()); serial.setText(printer.serial()); }
            if (active) printer.observe(MainActivity.this::render);
            render();
        }
        public void onServiceDisconnected(ComponentName name) { printer = null; render(); }
    };
    private final Runnable clock = new Runnable() {
        public void run() { if (active) { render(); main.postDelayed(this, 1000); } }
    };
    @Override public void onCreate(Bundle saved) {
        settings = getSharedPreferences("workshop-settings", MODE_PRIVATE);
        int appearance = settings.getInt("theme", 0);
        dark = appearance == 2 || appearance == 0 && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (dark) { INK = 0xffe4eff2; MUTED = 0xffa7bec6; TEAL = 0xff63d5c7; BACKGROUND = 0xff10191d; SURFACE = 0xff1b292f; BUTTON = 0xff223b3b; }
        setTheme(dark ? R.style.WorkshopDark : R.style.WorkshopLight);
        super.onCreate(saved);
        if (saved != null) { pendingExportHash = saved.getString("exportHash", ""); String uri = saved.getString("exportUri", ""); if (!uri.isEmpty()) pendingExportUri = android.net.Uri.parse(uri); }
        credentials = new CredentialStore(this);
        profiles = new ProfileStore(this);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BACKGROUND);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(24), dp(20), dp(24)); scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        label(content, "LINK WORKSHOP", 12, TEAL, true); label(content, "Your printer, on your phone", 25, INK, true);
        label(content, "Centauri Carbon 2 · local / VPN · v0.3.5", 14, MUTED, false);
        summary = label(content, "Disconnected · open Settings to connect", 14, TEAL, true);
        LinearLayout navigation = new LinearLayout(this); navigation.setOrientation(LinearLayout.HORIZONTAL); content.addView(navigation);
        String[] titles = {"Monitor", "Files", "Camera", "Settings"};
        for (int i = 0; i < 4; i++) { final int target = i; tabs[i] = new Button(this); styleButton(tabs[i]); tabs[i].setText(titles[i]); tabs[i].setTextSize(12); tabs[i].setPadding(dp(2), 0, dp(2), 0); tabs[i].setOnClickListener(v -> selectPage(target)); navigation.addView(tabs[i], new LinearLayout.LayoutParams(0, dp(48), 1)); }
        for (int i = 0; i < 4; i++) { pages[i] = new LinearLayout(this); pages[i].setOrientation(LinearLayout.VERTICAL); content.addView(pages[i]); }
        currentSection = pages[3];
        LinearLayout connectionCard = card("Connection");
        label(connectionCard, "Connection route", 13, MUTED, false);
        routePicker = spinner(connectionCard, new String[] {"Local Wi-Fi / Ethernet", "Remote through home VPN"});
        routePicker.setSelection(settings.getBoolean("remoteVPN", false) ? 1 : 0);
        routePicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) { }
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { settings.edit().putBoolean("remoteVPN", position == 1).apply(); stopCamera(); if (connect != null) { diagnostics.setText("Connection route changed. Run Check connection before reconnecting."); render(); } }
        });
        button(connectionCard, "Remote access setup…", this::remoteHelp);
        label(connectionCard, "Printer authentication", 13, MUTED, false);
        authPicker = spinner(connectionCard, new String[] {"LAN access code", "Cloud-mode PIN probe (read-only)"});
        authPicker.setSelection(settings.getBoolean("pinProbe", false) ? 1 : 0);
        authPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) { }
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { settings.edit().putBoolean("pinProbe", position == 1).apply(); if (connect != null) { diagnostics.setText("Authentication mode changed. Run Check connection; PIN probe preserves the printer's cloud setting."); render(); } }
        });
        button(connectionCard, "Matrix coexistence test…", this::coexistenceHelp);
        host = input(connectionCard, "Printer IP address", false); host.setInputType(InputType.TYPE_CLASS_PHONE);
        host.setText(credentials.host().isEmpty() ? getPreferences(MODE_PRIVATE).getString("host", "") : credentials.host());
        access = input(connectionCard, "LAN access code", true); access.setSaveEnabled(false); access.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        pairingPin = input(connectionCard, "Current printer pairing PIN (probe only)", true); pairingPin.setSaveEnabled(false); pairingPin.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        label(connectionCard, "PIN probe is experimental and read-only. Keep LAN Only off and Matrix working. Use the current printer-displayed pairing PIN, not the LAN access code. PINs are held only in memory; commands, uploads and automatic retries are disabled. Firmware may reject local PIN access.", 13, MUTED, false);
        serial = input(connectionCard, "Serial number (optional)", false);
        serial.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        label(connectionCard, "Leave serial blank for automatic identity discovery. A manual Serial Number from Settings → Device is used if discovery fails. Discovery also checks the printer's LAN mode and code-protection setting.", 13, MUTED, false);
        remember = new CheckBox(this); remember.setText("Remember access code securely on this phone"); remember.setChecked(credentials.remembers()); connectionCard.addView(remember);
        label(connectionCard, "IP: printer Settings → Network. LAN authentication uses Settings → LAN Only and its access code (blank if protection is off). The separate PIN probe tests local access while cloud mode stays enabled. Connection route independently selects home Wi-Fi or your home VPN/Pi gateway.", 13, MUTED, false);
        connection = label(connectionCard, "Preparing connection service…", 14, TEAL, true);
        connection.setTextIsSelectable(true);
        connect = button(connectionCard, "Connect", this::toggleConnection);
        check = button(connectionCard, "Check connection", this::checkConnection);
        diagnostics = label(connectionCard, "Check connection tests TCP reachability and UDP identity/mode. LAN authentication also checks HTTP system info. PIN probe never authenticates HTTP or includes its PIN in the report; Connect tests MQTT registration. HTTP uploads remain LAN-only and require port 80.", 13, MUTED, false);
        diagnostics.setTextIsSelectable(true);
        forget = button(connectionCard, "Forget saved access code", () -> { credentials.forget(host.getText().toString().trim()); remember.setChecked(false); access.setText(""); pairingPin.setText(""); message("Saved access code removed; entered PIN cleared. An existing connection keeps its in-memory code until disconnected."); });
        remember.setTextColor(INK); remember.setButtonTintList(tint(TEAL));
        discover = button(connectionCard, "Find printers on Wi-Fi", this::scanPrinters);
        profileName = input(connectionCard, "Printer profile name", false);
        serial.setText(profiles.find(host.getText().toString()).optString("serial"));
        profileName.setText(profiles.find(host.getText().toString()).optString("name"));
        saveProfile = button(connectionCard, "Save printer profile", this::savePrinter);
        chooseProfile = button(connectionCard, "Choose saved printer", this::choosePrinter);
        removeProfile = button(connectionCard, "Remove this profile…", this::removePrinter);
        currentSection = pages[0];
        LinearLayout monitor = card("Live monitor");
        identity = label(monitor, "Connect to identify the printer and firmware.", 13, MUTED, false);
        state = label(monitor, "Waiting for printer", 22, INK, true);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100); monitor.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
        temperatures = label(monitor, "Nozzle —    Bed —    Chamber —", 16, INK, false);
        job = label(monitor, "Connect to see the current print, layers and remaining time.", 14, MUTED, false);
        faults = label(monitor, "Printer faults will appear here after status is received.", 14, MUTED, false);
        refresh = button(monitor, "Refresh status & trays", () -> { if (printer != null) printer.refresh(); });
        LinearLayout canvas = card("CANVAS filament trays");
        trays = label(canvas, "Connect to see reported trays, materials, colors and the active tray.", 15, INK, false);
        label(canvas, "Tray IDs and state codes are shown as reported by the printer. Unknown values are not guessed.", 12, MUTED, false);
        refill = button(canvas, "Automatic refill", this::confirmRefill);
        LinearLayout controls = card("Print controls");
        pause = button(controls, "Pause print", () -> confirmCommand("Pause the current print?", Cc2Codec.PAUSE));
        resume = button(controls, "Resume print…", () -> confirmCommand("Resume after checking why the printer paused?", Cc2Codec.RESUME));
        stop = button(controls, "Stop print…", () -> confirmCommand("Stop the current print? It cannot be resumed through this app.", Cc2Codec.STOP));
        label(controls, "Controls require fresh status and an appropriate printer state. Printer-changing commands are never automatically replayed.", 13, MUTED, false);
        LinearLayout tuning = card("Printer settings");
        lightOn = button(tuning, "Chamber light on", () -> { if (printer != null) printer.light(true); });
        lightOff = button(tuning, "Chamber light off", () -> { if (printer != null) printer.light(false); });
        heater = button(tuning, "Temperature targets…", this::temperatureDialog);
        fan = button(tuning, "Fan setting…", this::fanDialog);
        speed = button(tuning, "Print speed mode…", this::speedDialog);
        label(tuning, "Temperature targets are available while idle. Firmware can reject an unavailable setting; acknowledgements and updated status are shown separately.", 13, MUTED, false);
        currentSection = pages[1];
        LinearLayout files = card("Phone G-code workspace");
        selected = label(files, "Choose a .gcode file sliced for this printer.", 14, MUTED, false);
        pick = button(files, "Choose G-code", () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(intent, PICK_FILE);
        });
        inspection = label(files, "Select a G-code file to inspect it offline. Printer connection is not required.", 13, INK, false); inspection.setTextIsSelectable(true);
        filePreview = new ImageView(this); filePreview.setContentDescription("Embedded slicer preview of selected G-code"); filePreview.setScaleType(ImageView.ScaleType.FIT_CENTER); files.addView(filePreview, new LinearLayout.LayoutParams(-1,dp(200))); filePreview.setVisibility(View.GONE);
        previewInfo = label(files, "Embedded previews appear when a supported image is present in the selected G-code.", 13, MUTED, false);
        materialDetails = button(files, "Material details…", () -> {
            if (printer == null || printer.selectedReport == null) return;
            SlicedMaterials materials = printer.selectedReport.materials; LinearLayout body = dialogBody();
            for (SlicedMaterials.Entry entry : materials.entries) {
                LinearLayout row = new LinearLayout(this); body.addView(row);
                if (entry.color != null) { View swatch = new View(this); swatch.setContentDescription("Configured filament color " + entry.color); swatch.setBackgroundColor(Color.parseColor(entry.color)); LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(28),dp(28)); size.setMargins(0,dp(8),dp(12),0); row.addView(swatch,size); }
                label(row, entry.text(), 14, INK, false);
            }
            for (String warning : materials.warnings) label(body, warning, 13, MUTED, false);
            label(body, "Comment indices are source-array positions, not confirmed tool or CANVAS tray IDs. No mapping is applied.", 13, MUTED, false);
            ScrollView detail = new ScrollView(this); detail.addView(body); new AlertDialog.Builder(this).setTitle("Sliced material evidence").setView(detail).setPositiveButton("Close",null).show();
        });
        shareInspection = button(files, "Share inspection report…", () -> {
            if (printer == null || printer.selectedReport == null) return;
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, StatusPresentation.clean(printer.selectedName) + "\n" + printer.selectedReport.text());
            startActivity(Intent.createChooser(send, "Share G-code inspection"));
        });
        saveCopy = button(files, "Save phone copy…", () -> {
            if (printer == null || printer.selectedReport == null || printer.fileBusy()) return;
            pendingExportHash = printer.selectedReport.sha256;
            Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, printer.selectedName);
            startActivityForResult(save, SAVE_GCODE);
        });
        clearCopy = button(files, "Remove phone cache copy", () -> { if (printer != null) printer.clearPhoneCopy(); });
        upload = button(files, "Upload to printer", () -> {
            if (printer == null || printer.selectedName == null) return;
            new AlertDialog.Builder(this).setTitle("Upload " + printer.selectedName + "?")
                .setMessage("This sends the file only. A file with the same name may be replaced. Refresh Files after upload and choose Print setup to start it.")
                .setNegativeButton("Cancel", null).setPositiveButton("Upload", (dialog, which) -> { if (printer != null) printer.upload(); }).show();
        });
        cancelUpload = button(files, "Cancel upload", () -> { if (printer != null) printer.cancelUpload(); });
        cancelDownload = button(files, "Cancel download", () -> { if (printer != null) printer.cancelDownload(); });
        label(files, "Downloaded files become phone cache copies here; use Save phone copy to keep a document. Downloads require LAN authentication and printer HTTP port 80. Offline reports read slicer comments and explicit T selections; they do not simulate a print or assign trays.", 13, MUTED, false);
        label(files, "Connection and uploads continue while choosing a file, rotating, or switching apps. Use Disconnect in the app or notification to stop the session. Android may still stop the app under memory or battery restrictions.", 13, MUTED, false);
        label(files, "File uploads require HTTP port 80. If HTTP is unavailable, you can still monitor and use supported MQTT controls.", 13, MUTED, false);
        buildFileBrowser();
        currentSection = pages[2]; buildCamera();
        currentSection = pages[3];
        LinearLayout preferences = card("App preferences");
        button(preferences, "Appearance: " + (settings.getInt("theme", 0) == 0 ? "System" : dark ? "Dark" : "Light"), this::appearanceDialog);
        CheckBox alerts = checkbox(preferences, "Completion and new fault notifications", settings.getBoolean("alerts", true));
        alerts.setOnCheckedChangeListener((view, enabled) -> settings.edit().putBoolean("alerts", enabled).apply());
        label(preferences, "Alerts require notification permission and an active monitoring session. They do not run after you disconnect or Android stops the process.", 13, MUTED, false);
        LinearLayout coming = card("Next in the workshop");
        label(coming, "Official cloud login · timelapse export · filament loading · other printer models", 15, INK, false);
        label(coming, "These features remain pending. New controls, files and camera behavior still need testing with your printer; unsupported requests report their error or timeout.", 13, MUTED, false);
        button(coming, "About & licenses", this::showLicenses);
        currentSection = null; feedback = label(content, "Development build: printer behavior still needs hardware testing.", 14, MUTED, false);
        if (saved != null) { host.setText(saved.getString("host", host.getText().toString())); serial.setText(saved.getString("serial", "")); diagnostics.setText(saved.getString("diagnostics", diagnostics.getText().toString())); profileName.setText(saved.getString("profileName", profileName.getText().toString())); }
        try { access.setText(credentials.load(host.getText().toString().trim())); remember.setChecked(saved == null ? credentials.remembers(host.getText().toString().trim()) : saved.getBoolean("remember", false)); }
        catch (Exception error) { credentials.forget(host.getText().toString().trim()); remember.setChecked(false); message("Saved code could not be decrypted. Enter it again before connecting."); }
        TransientInputs transientInputs = (TransientInputs) getLastNonConfigurationInstance();
        if (transientInputs != null && host.getText().toString().equals(transientInputs.host)) { access.setText(transientInputs.code); pendingSnapshot = transientInputs.snapshot; }
        selectPage(saved == null ? settings.getInt("page", 3) : saved.getInt("page", 3));
        bound = bindService(new Intent(this, PrinterService.class), binding, BIND_AUTO_CREATE);
        render();
    }
    private void toggleConnection() {
        if (printer == null) return;
        if (printer.connecting()) { printer.disconnect(); return; }
        String address = host.getText().toString().trim(), code = pinProbe() ? pairingPin.getText().toString() : access.getText().toString();
        String serialNumber = serial.getText().toString().trim();
        if (!serialNumber.isEmpty() && !Cc2Discovery.validSerial(serialNumber)) { message("Enter the exact printer serial, using letters, numbers, hyphens or underscores, without spaces."); return; }
        try { new PrinterAuthentication(pinProbe(), code); new PrinterHttp(address, pinProbe() ? "" : code); }
        catch (Exception error) { message(pinProbe() ? "Enter a valid private printer IP and its current displayed pairing PIN." : "Enter a valid private IPv4 address and LAN access code."); return; }
        try { if (!pinProbe()) credentials.save(address, code, remember.isChecked()); }
        catch (Exception error) { message("Could not save the code securely. Uncheck Remember to connect without saving."); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            && !getPreferences(MODE_PRIVATE).getBoolean("notificationsAsked", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationsAsked", true).apply();
            requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 2);
        }
        try {
            startForegroundService(new Intent(this, PrinterService.class));
            printer.connect(address, code, serialNumber, remoteMode(), pinProbe()); render();
        } catch (Exception error) { printer.disconnect(); message("Android could not start printer monitoring. Keep the app open and check its permissions."); }
    }
    private void checkConnection() {
        if (checking) return;
        String address = host.getText().toString().trim(), code = pinProbe() ? "" : access.getText().toString();
        String serialNumber = serial.getText().toString().trim();
        if (!serialNumber.isEmpty() && !Cc2Discovery.validSerial(serialNumber)) { diagnostics.setText("Enter a valid serial number or leave it blank for discovery."); return; }
        try { new PrinterHttp(address, code); } catch (Exception error) { diagnostics.setText("Enter a valid private IPv4 address before checking."); return; }
        checking = true; diagnostics.setText(remoteMode() ? "Checking VPN, HTTP 80, MQTT 1883, camera 8080, and UDP identity 52700…" : "Checking Wi-Fi, HTTP 80, MQTT 1883, and UDP discovery 52700…"); render();
        Context context = getApplicationContext(); boolean remote = remoteMode(), probe = pinProbe();
        checks.execute(() -> {
            String report;
            try { report = NetworkRoute.select(context, remote).check(address, code, serialNumber, probe); }
            catch (IOException error) { report = remote ? "Home VPN is unavailable or changed. Enable Tailscale / your home VPN, verify this app uses it, and check again." : "No local Wi-Fi network is available. Connect the phone to the printer's Wi-Fi and check again."; }
            catch (Exception error) { report = "Connection check failed. Confirm the current printer IP and the phone's Wi-Fi."; }
            String result = report;
            main.post(() -> { if (!isDestroyed()) { checking = false; diagnostics.setText(result); render(); } });
        });
    }
    private void confirmCommand(String title, int method) {
        new AlertDialog.Builder(this).setTitle(title).setNegativeButton("Cancel", null)
            .setPositiveButton(method == Cc2Codec.PAUSE ? "Pause" : method == Cc2Codec.RESUME ? "Resume" : "Stop", (dialog, which) -> { if (printer != null) printer.command(method); }).show();
    }
    private void confirmRefill() {
        if (printer == null || !printer.canvasFresh() || !printer.canvas.has("auto_refill")) return;
        boolean enabled = !printer.canvas.optBoolean("auto_refill");
        new AlertDialog.Builder(this).setTitle((enabled ? "Enable" : "Disable") + " automatic refill?")
            .setMessage("This changes the printer's CANVAS automatic-refill setting. Tray selection and filament loading remain controlled by the printer.")
            .setNegativeButton("Cancel", null).setPositiveButton(enabled ? "Enable" : "Disable", (dialog, which) -> { if (printer != null) printer.autoRefill(enabled); }).show();
    }
    private void selectPage(int selected) {
        page = Math.max(0, Math.min(3, selected));
        if (page != 2) stopCamera();
        for (int i = 0; i < 4; i++) { pages[i].setVisibility(i == page ? View.VISIBLE : View.GONE); tabs[i].setTypeface(Typeface.DEFAULT, i == page ? Typeface.BOLD : Typeface.NORMAL); tabs[i].setAlpha(i == page ? 1f : 0.7f); }
        settings.edit().putInt("page", page).apply();
    }
    private boolean pinProbe() { return authPicker != null && authPicker.getSelectedItemPosition() == 1; }
    private boolean remoteMode() { return routePicker != null && routePicker.getSelectedItemPosition() == 1; }
    private void coexistenceHelp() {
        new AlertDialog.Builder(this).setTitle("Read-only Matrix coexistence test")
            .setMessage("1. Leave the printer in normal cloud mode (LAN Only off). Confirm Matrix shows live status/camera. Do not unbind or re-pair the printer.\n\n2. Use home Wi-Fi first. Select Cloud-mode PIN probe and enter the current printer-displayed pairing PIN if available. Supply the exact serial if UDP discovery cannot identify it.\n\n3. Check connection, then Connect. This tries the SDK's local MQTT PIN path once. It does not sign into your Elegoo account, bind devices, copy cloud client identities, guess credentials or use a PIN in HTTP.\n\n4. Compare live status here and in Matrix, switch between apps, and confirm Matrix remains connected. Registration alone does not prove coexistence. If refused or disconnected, the probe stops; firmware may require the official cloud transport.\n\nPrinter-changing controls and uploads stay disabled in this probe. Camera/read queries depend on firmware. PINs are not saved, included in diagnostics or logged. If no monitoring session remains, reopening the app requires the PIN again.")
            .setPositiveButton("Close", null).show();
    }
    private void remoteHelp() {
        LinearLayout body = dialogBody();
        label(body, "Use your always-on Pi as a Tailscale subnet router. Install Tailscale on the Pi and phone, then sign in to your own tailnet. The Pi needs access to the printer on your home network.", 14, INK, false);
        label(body, "On the Pi: enable IPv4 forwarding and advertise only the printer's IP as a /32 route. Approve that route in the Tailscale admin console. Restrict the phone's access to printer TCP 1883 (monitor/control), 80 (uploads if available), 8080 (camera) and optionally UDP 52700 (identity). Broader existing access rules must also be reviewed.", 14, INK, false);
        label(body, "Choose Remote through home VPN and keep the printer's home IP (not the Pi's Tailscale IP). Authentication is separate: LAN access code uses LAN Only; the experimental read-only PIN probe keeps cloud mode enabled to test Matrix coexistence. Establish the selected authentication locally first. A manual serial provides fallback if UDP identity does not reply.", 14, INK, false);
        label(body, "The internet hop from phone to Pi is encrypted by the VPN. The Pi-to-printer hop retains the printer's LAN protocol. Do not publicly forward printer ports. Secure your account with MFA and keep the Pi updated. Android's always-on VPN/block-without-VPN setting provides stronger enforcement against connection-loss races. VPN presence alone does not prove the gateway/route or encryption configuration.", 14, MUTED, false);
        label(body, "The app does not install or configure Tailscale, and it does not bypass printer authentication. HTTP unavailable at home stays unavailable remotely. Monitoring/control/upload/camera requests use the selected route; VPN loss closes the session and requires a fresh connection without command replay.", 14, MUTED, false);
        ScrollView scroll = new ScrollView(this); scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("Away-from-home access").setView(scroll).setPositiveButton("Close", null).show();
    }
    private void appearanceDialog() {
        new AlertDialog.Builder(this).setTitle("Appearance").setSingleChoiceItems(new String[] {"Follow system", "Light", "Dark"}, settings.getInt("theme", 0), (dialog, which) -> {
            settings.edit().putInt("theme", which).apply(); dialog.dismiss(); recreate();
        }).setNegativeButton("Cancel", null).show();
    }
    private void savePrinter() {
        String address = host.getText().toString().trim(), code = access.getText().toString();
        try {
            new PrinterHttp(address, pinProbe() ? "" : code);
            profiles.save(profileName.getText().toString().trim(), address, serial.getText().toString().trim(), remoteMode(), pinProbe());
            if (!pinProbe()) credentials.save(address, code, remember.isChecked()); message(pinProbe() ? "Profile saved with PIN-probe mode. The PIN is not saved." : "Printer profile saved. Access-code storage follows the Remember setting.");
        } catch (Exception error) { message("Profile could not be saved. Check the IP and serial, or uncheck Remember if credential storage is unavailable."); }
    }
    private void choosePrinter() {
        JSONArray rows = profiles.all(); if (rows.length() == 0) { message("No saved printers. Enter an IP or use Find printers, then Save printer profile."); return; }
        String[] names = new String[rows.length()];
        for (int i = 0; i < names.length; i++) { JSONObject row = rows.optJSONObject(i); names[i] = row == null ? "Invalid profile" : row.optString("name") + " · " + row.optString("host"); }
        new AlertDialog.Builder(this).setTitle("Saved printers").setItems(names, (dialog, which) -> {
            JSONObject row = rows.optJSONObject(which); if (row != null) { selectPrinter(row.optString("host"), row.optString("serial"), row.optString("name")); routePicker.setSelection(row.optBoolean("remote_vpn", false) ? 1 : 0); authPicker.setSelection(row.optBoolean("pin_probe", false) ? 1 : 0); }
        }).setNegativeButton("Cancel", null).show();
    }
    private void selectPrinter(String address, String number, String name) {
        if (printer != null && printer.connecting()) { message("Disconnect before selecting another printer."); return; }
        stopCamera(); pairingPin.setText(""); host.setText(address); serial.setText(number); profileName.setText(name); access.setText(""); remember.setChecked(credentials.remembers(address));
        try { access.setText(credentials.load(address)); } catch (Exception error) { credentials.forget(address); remember.setChecked(false); message("Saved code could not be decrypted. Enter it again."); }
        cameraHost = ""; diagnostics.setText("Run Check connection to inspect this printer."); render();
    }
    private void removePrinter() {
        String address = host.getText().toString().trim();
        new AlertDialog.Builder(this).setTitle("Remove saved profile?").setMessage("This removes the profile and its saved access code from this phone.")
            .setNegativeButton("Cancel", null).setPositiveButton("Remove", (dialog, which) -> { profiles.remove(address); credentials.forget(address); access.setText(""); remember.setChecked(false); profileName.setText(""); message("Saved profile removed."); }).show();
    }
    private void scanPrinters() {
        if (remoteMode()) { message("Wi-Fi broadcast scanning is unavailable over a routed VPN. Enter the home printer IP and serial, or use a saved profile."); return; }
        if (scanningNow) return;
        scanningNow = true; render();
        checks.execute(() -> {
            List<Cc2Discovery.Found> found = Collections.emptyList(); String failure = "";
            try (Cc2Discovery scanner = NetworkRoute.local(getApplicationContext()).discovery()) { scanning = scanner; found = scanner.scan(); }
            catch (Exception error) { failure = "Could not scan local Wi-Fi. Confirm the phone is connected to the printer's network."; }
            finally { scanning = null; }
            List<Cc2Discovery.Found> result = found; String error = failure;
            main.post(() -> {
                if (isDestroyed()) return; scanningNow = false; render();
                if (!error.isEmpty()) { message(error); return; }
                if (result.isEmpty()) { message("No CC2 discovery replies received. You can still enter the printer IP and serial manually; some networks block broadcasts."); return; }
                String[] names = new String[result.size()];
                for (int i = 0; i < names.length; i++) { Cc2Discovery.Found entry = result.get(i); names[i] = StatusPresentation.clean(entry.info.name.isEmpty() ? entry.info.model : entry.info.name) + " · " + entry.host + "\n" + entry.info.summary(); }
                new AlertDialog.Builder(this).setTitle("Printers found").setItems(names, (dialog, which) -> {
                    Cc2Discovery.Found entry = result.get(which); selectPrinter(entry.host, entry.info.serial, entry.info.name); message(entry.info.summary());
                }).setNegativeButton("Cancel", null).show();
            });
        });
    }
    private void buildFileBrowser() {
        LinearLayout browser = card("Printer files");
        storagePicker = spinner(browser, new String[] {"Internal storage", "USB drive"});
        storagePicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) { }
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { if (printer != null && printer.ready() && !printer.busy(Cc2Codec.FILES)) printer.browse(position == 0 ? "local" : "u-disk", 0); }
        });
        listFiles = button(browser, "Refresh files", () -> { if (printer != null) printer.browse(storagePicker.getSelectedItemPosition() == 0 ? "local" : "u-disk", 0); });
        fileInfo = label(browser, "Connect, then refresh to browse printer files.", 14, MUTED, false);
        fileRows = new LinearLayout(this); fileRows.setOrientation(LinearLayout.VERTICAL); browser.addView(fileRows);
        previousFiles = button(browser, "Previous 50 files", () -> { if (printer != null) printer.browse(printer.storage, Math.max(0, printer.fileOffset - 50)); });
        nextFiles = button(browser, "Next 50 files", () -> { if (printer != null) printer.browse(printer.storage, printer.fileOffset + 50); });
        label(browser, "Select a G-code file for metadata, print setup or deletion. Starting and deleting require fresh status, a recent file list, and an idle printer.", 13, MUTED, false);
        LinearLayout storage = card("Storage & print history");
        diskInfo = label(storage, "Storage usage not loaded.", 14, MUTED, false);
        loadDisk = button(storage, "Refresh storage usage", () -> { if (printer != null) printer.loadDisk(); });
        loadHistory = button(storage, "Refresh print history", () -> { if (printer != null) printer.loadHistory(); });
        historyInfo = label(storage, "History not loaded.", 14, INK, false); historyInfo.setTextIsSelectable(true);
    }
    private Spinner spinner(LinearLayout parent, String[] items) {
        Spinner view = new Spinner(this); ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); view.setAdapter(adapter); view.setBackgroundTintList(tint(TEAL)); parent.addView(view, new LinearLayout.LayoutParams(-1, dp(52))); return view;
    }
    private LinearLayout dialogBody() { LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(20), dp(8), dp(20), dp(8)); return body; }
    private void fileActions(JSONObject file, String storage) {
        String name = file.optString("filename");
        try { Cc2Codec.filename(name); } catch (Exception error) { message("Only .gcode files can be started or deleted here."); return; }
        LinearLayout body = dialogBody(); label(body, FeatureData.file(file), 14, INK, false);
        ScrollView detailScroll = new ScrollView(this); detailScroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Printer file").setView(detailScroll).setNegativeButton("Close", null).create();
        if (printer != null && printer.pinProbe()) { label(body, "Read-only PIN probe: print start and deletion are disabled.", 14, MUTED, false); dialog.show(); return; }
        Button download = button(body, "Download to phone workspace", () -> { dialog.dismiss(); if (printer != null) printer.download(storage, name); });
        download.setEnabled(printer != null && printer.filesFresh() && !printer.fileBusy());
        button(body, "Print setup…", () -> { dialog.dismiss(); startDialog(file, storage); });
        button(body, "Delete file…", () -> { dialog.dismiss(); new AlertDialog.Builder(this).setTitle("Delete " + StatusPresentation.clean(name) + "?").setMessage("This permanently removes the selected file from the printer. The printer must be idle.")
            .setNegativeButton("Cancel", null).setPositiveButton("Delete", (d, which) -> { if (printer != null) printer.delete(storage, name); }).show(); });
        dialog.show();
    }
    private void startDialog(JSONObject file, String storage) {
        if (printer != null && printer.pinProbe()) { message("Read-only PIN probe: print start is disabled."); return; }
        if (printer == null || !printer.fresh() || !Cc2Codec.idle(printer.status) || !printer.filesFresh()) { message("Refresh status and files, then wait for the printer to be idle."); return; }
        String name = file.optString("filename");
        LinearLayout body = dialogBody(); label(body, StatusPresentation.clean(name) + "\nCheck the build plate, material and sliced printer profile before starting.", 14, INK, false);
        CheckBox leveling = checkbox(body, "Run printer / bed check", true), force = checkbox(body, "Force bed leveling", false), timelapse = checkbox(body, "Record timelapse on printer", false);
        force.setOnCheckedChangeListener((view, checked) -> { if (checked) leveling.setChecked(true); });
        leveling.setOnCheckedChangeListener((view, checked) -> { if (!checked) force.setChecked(false); });
        label(body, "Build plate selection (as reported by the protocol)", 13, MUTED, false);
        Spinner plate = spinner(body, new String[] {"Plate A", "Plate B"});
        label(body, "Number of tools used in this sliced file", 13, MUTED, false);
        Spinner toolCount = spinner(body, new String[] {"1 tool", "2 tools", "3 tools", "4 tools", "5 tools", "6 tools", "7 tools", "8 tools"});
        List<JSONObject> trays = new ArrayList<>(); List<String> choices = new ArrayList<>(); choices.add("Printer / G-code default");
        if (printer.canvasFresh()) {
            JSONArray units = printer.canvas.optJSONArray("canvas_list");
            if (units != null) for (int u = 0; u < units.length(); u++) {
                JSONObject unit = units.optJSONObject(u); if (unit == null || !(Boolean.TRUE.equals(unit.opt("connected")) || unit.optInt("connected", 0) == 1)) continue;
                JSONArray slots = unit.optJSONArray("tray_list"); if (slots == null) continue;
                for (int t = 0; t < slots.length(); t++) {
                    JSONObject slot = slots.optJSONObject(t); if (slot == null || slot.optString("filament_type").isEmpty() || unit.optInt("canvas_id", -1) < 0 || slot.optInt("tray_id", -1) < 0) continue;
                    try { trays.add(new JSONObject().put("canvas_id", unit.getInt("canvas_id")).put("tray_id", slot.getInt("tray_id"))); }
                    catch (Exception error) { continue; }
                    choices.add("CANVAS " + unit.optInt("canvas_id") + " · Tray " + slot.optInt("tray_id") + " · " + StatusPresentation.clean(slot.optString("filament_type")) + " " + StatusPresentation.clean(slot.optString("filament_color")));
                }
            }
        }
        Spinner[] maps = new Spinner[8]; TextView[] labels = new TextView[8];
        for (int t = 0; t < 8; t++) { labels[t] = label(body, "G-code tool " + t, 13, MUTED, false); maps[t] = spinner(body, choices.toArray(new String[0])); }
        toolCount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) { }
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { for (int t = 0; t < 8; t++) { maps[t].setVisibility(t <= position ? View.VISIBLE : View.GONE); labels[t].setVisibility(t <= position ? View.VISIBLE : View.GONE); } }
        });
        label(body, "Verify the tool count against your sliced file. Leave every tool at default to use the printer / G-code mapping, or explicitly map every tool to a reported tray. Timelapse records on the printer; export is not included yet.", 13, MUTED, false);
        ScrollView scroll = new ScrollView(this); scroll.addView(body);
        AlertDialog setup = new AlertDialog.Builder(this).setTitle("Print setup").setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Review start…", null).create();
        setup.setOnShowListener(d -> setup.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            JSONArray mapping = new JSONArray(); int count = toolCount.getSelectedItemPosition() + 1, explicit = 0;
            try {
                for (int t = 0; t < count; t++) { int selected = maps[t].getSelectedItemPosition(); if (selected > 0) { JSONObject tray = new JSONObject(trays.get(selected - 1).toString()); tray.put("t", t); mapping.put(tray); explicit++; } }
                if (explicit != 0 && explicit != count) { message("Map every used tool, or leave all tools at Printer / G-code default."); new AlertDialog.Builder(this).setMessage("Map every used tool, or leave all tools at default.").setPositiveButton("OK", null).show(); return; }
            } catch (Exception error) { message("Could not prepare tool mappings. Refresh trays."); return; }
            setup.dismiss();
            String text = StatusPresentation.clean(name) + "\nStorage: " + (storage.equals("local") ? "Internal" : "USB") + "\nPlate " + (plate.getSelectedItemPosition() == 0 ? "A" : "B")
                + " · Bed check " + (leveling.isChecked() ? "on" : "off") + " · Force leveling " + (force.isChecked() ? "on" : "off") + "\nTimelapse " + (timelapse.isChecked() ? "on" : "off")
                + "\n" + count + " tool(s): " + (mapping.length() == 0 ? "printer / G-code default mapping" : "explicit reported tray mappings") + "\n\nStarting moves and heats the printer. Confirm the plate is clear and the filament is correct.";
            new AlertDialog.Builder(this).setTitle("Start this print?").setMessage(text).setNegativeButton("Cancel", null).setPositiveButton("Start print", (confirm, which) -> {
                if (printer != null) printer.start(storage, name, leveling.isChecked(), force.isChecked(), timelapse.isChecked(), plate.getSelectedItemPosition() == 0 ? "A" : "B", mapping);
            }).show();
        })); setup.show();
    }
    private void temperatureDialog() {
        LinearLayout body = dialogBody(); label(body, "While idle: nozzle 0–300°C, bed 0–100°C. Zero turns that heater off. Use the correct targets for your filament and build plate.", 14, INK, false);
        EditText nozzle = input(body, "Nozzle target °C", false), bed = input(body, "Bed target °C", false); nozzle.setInputType(InputType.TYPE_CLASS_NUMBER); bed.setInputType(InputType.TYPE_CLASS_NUMBER);
        JSONObject n = snapshot.optJSONObject("extruder"), b = snapshot.optJSONObject("heater_bed"); nozzle.setText(String.valueOf(n == null ? 0 : n.optInt("target"))); bed.setText(String.valueOf(b == null ? 0 : b.optInt("target")));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Temperature targets").setView(body).setNegativeButton("Cancel", null).setPositiveButton("Set targets", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { try { int nt = Integer.parseInt(nozzle.getText().toString()), bt = Integer.parseInt(bed.getText().toString()); Cc2Codec.temperatureRequest(0, nt, bt); if (printer != null) printer.temperatures(nt, bt); dialog.dismiss(); } catch (Exception error) { nozzle.setError("Nozzle 0–300°C; bed 0–100°C"); } })); dialog.show();
    }
    private void fanDialog() {
        LinearLayout body = dialogBody(); Spinner kind = spinner(body, new String[] {"Part cooling", "Auxiliary", "Chamber"});
        TextView value = label(body, "Fan: 0%", 15, INK, false); SeekBar amount = new SeekBar(this); amount.setMax(100); body.addView(amount);
        amount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar bar, int progress, boolean user) { value.setText("Fan: " + progress + "%"); } public void onStartTrackingTouch(SeekBar bar) { } public void onStopTrackingTouch(SeekBar bar) { } });
        new AlertDialog.Builder(this).setTitle("Fan setting").setView(body).setNegativeButton("Cancel", null).setPositiveButton("Set fan", (dialog, which) -> { if (printer != null) printer.fan(new String[] {"fan", "aux_fan", "box_fan"}[kind.getSelectedItemPosition()], amount.getProgress()); }).show();
    }
    private void speedDialog() { new AlertDialog.Builder(this).setTitle("Print speed mode").setItems(new String[] {"Silent", "Balanced", "Sport", "Ludicrous"}, (dialog, which) -> {
        new AlertDialog.Builder(this).setTitle("Change print speed?").setMessage("This changes the current print's speed mode. Filament changes may reset the mode on some firmware.").setNegativeButton("Cancel", null).setPositiveButton("Apply", (d, w) -> { if (printer != null) printer.speed(which); }).show();
    }).setNegativeButton("Cancel", null).show(); }
    private void buildCamera() {
        LinearLayout card = card("Live camera");
        cameraAddress = input(card, "Camera URL on this printer", false); cameraAddress.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        cameraQuery = button(card, "Get camera address from printer", () -> { if (printer != null) printer.camera(); });
        cameraInfo = label(card, "Printer JPEG stream through the selected connection route. Start is independent of MQTT authentication; the default CC2 endpoint uses port 8080.", 14, MUTED, false);
        cameraImage = new ImageView(this); cameraImage.setContentDescription("Live printer camera"); cameraImage.setScaleType(ImageView.ScaleType.FIT_CENTER); cameraImage.setBackgroundColor(Color.BLACK); card.addView(cameraImage, new LinearLayout.LayoutParams(-1, dp(240)));
        cameraStart = button(card, "Start camera", this::toggleCamera);
        cameraSnapshot = button(card, "Save snapshot…", () -> {
            if (lastFrame == null) return; pendingSnapshot = lastFrame.copy(Bitmap.Config.ARGB_8888, false);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("image/jpeg").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "CC2-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + ".jpg"); startActivityForResult(intent, PICK_SNAPSHOT);
        });
        button(card, "Larger camera view", () -> { cameraImage.getLayoutParams().height = cameraImage.getLayoutParams().height == dp(240) ? dp(400) : dp(240); cameraImage.requestLayout(); });
        label(card, "Camera stops when you leave this tab or put the app in the background. Printer monitoring continues in its foreground service. Redirects and camera addresses on other devices are blocked.", 13, MUTED, false);
    }
    private void toggleCamera() {
        if (cameraPlayer != null) { stopCamera(); return; }
        try {
            String address = host.getText().toString().trim(), url = FeatureData.cameraUrl(address, cameraAddress.getText().toString().trim());
            NetworkRoute route = NetworkRoute.select(this, remoteMode()); cameraInfo.setText("Opening camera…"); cameraRoute = route;
            cameraRouteWatch = route.watch(() -> main.post(() -> { if (cameraRoute == route && cameraPlayer != null) { stopCamera(); cameraInfo.setText("Home VPN route changed. Enable the VPN and restart the camera."); } }));
            cameraPlayer = new MjpegPlayer(url, route.http(), new MjpegPlayer.Listener() {
                public void frame(Bitmap image) { if (isDestroyed()) return; lastFrame = image; cameraImage.setImageBitmap(image); cameraInfo.setText("Live camera · up to 5 frames/second"); cameraSnapshot.setEnabled(true); }
                public void error(String text) { stopCamera(); cameraInfo.setText(text); }
            }); cameraStart.setText("Stop camera");
        } catch (Exception error) { stopCamera(); cameraInfo.setText(remoteMode() ? "Enable your home VPN and use the camera URL on the home printer IP. The Pi/subnet route must allow its camera port." : "Enter a camera URL on the selected printer's IP and connect the phone to local Wi-Fi."); }
    }
    private void stopCamera() { AutoCloseable watcher = cameraRouteWatch; cameraRouteWatch = null; cameraRoute = null; if (watcher != null) try { watcher.close(); } catch (Exception ignored) { } MjpegPlayer player = cameraPlayer; cameraPlayer = null; if (player != null) player.close(); if (cameraStart != null) cameraStart.setText("Start camera"); }
    private void renderFeatures(boolean ready, boolean fresh) {
        listFiles.setEnabled(ready && !printer.busy(Cc2Codec.FILES)); storagePicker.setEnabled(!ready || !printer.busy(Cc2Codec.FILES));
        loadHistory.setEnabled(ready && !printer.busy(Cc2Codec.HISTORY)); loadDisk.setEnabled(ready && !printer.busy(Cc2Codec.DISK)); cameraQuery.setEnabled(ready && !printer.busy(Cc2Codec.CAMERA));
        JSONObject files = printer == null ? new JSONObject() : printer.filePage;
        JSONArray rows = files.optJSONArray("file_list"); int count = rows == null ? 0 : rows.length(), offset = printer == null ? 0 : printer.fileOffset;
        previousFiles.setEnabled(ready && !printer.busy(Cc2Codec.FILES) && offset > 0);
        nextFiles.setEnabled(ready && !printer.busy(Cc2Codec.FILES) && count >= 50 && (files.optInt("total", -1) < 0 || offset + count < files.optInt("total")));
        fileInfo.setText(printer == null ? "Connect to browse printer files." : printer.fileMessage + (rows == null ? "" : "\n" + (printer.storage.equals("local") ? "Internal" : "USB") + " · " + count + " file(s) · offset " + offset + (printer.filesFresh() ? "" : " · list stale")));
        if (files != renderedFiles) {
            renderedFiles = files; fileRows.removeAllViews();
            if (rows != null) for (int i = 0; i < Math.min(rows.length(), 50); i++) {
                JSONObject row = rows.optJSONObject(i); if (row == null) continue; String storage = printer.storage;
                button(fileRows, StatusPresentation.clean(row.optString("filename", "Unnamed entry")), () -> fileActions(row, storage));
                label(fileRows, FeatureData.size(row.optLong("size", -1)) + (row.has("layer") ? " · " + row.optInt("layer") + " layers" : ""), 12, MUTED, false);
            }
        }
        diskInfo.setText(printer == null || printer.disk.length() == 0 ? "Storage usage not loaded." : FeatureData.disk(printer.disk));
        historyInfo.setText(printer == null ? "History not loaded." : printer.history.length() == 0 ? printer.historyMessage : FeatureData.history(printer.history));
        String selectedHost = host.getText().toString().trim();
        if (!cameraHost.equals(selectedHost)) { stopCamera(); cameraHost = selectedHost; cameraReported = ""; cameraAddress.setText(selectedHost.isEmpty() ? "" : "http://" + selectedHost + ":8080/?action=stream"); lastFrame = null; cameraImage.setImageDrawable(null); }
        if (ready && !printer.cameraUrl.isEmpty() && cameraPlayer == null && !printer.cameraUrl.equals(cameraReported)) { cameraAddress.setText(printer.cameraUrl); cameraReported = printer.cameraUrl; }
        cameraSnapshot.setEnabled(lastFrame != null);
    }
    private void render() {
        if (connect == null || isDestroyed()) return;
        if (cameraPlayer != null && cameraRoute != null && !cameraRoute.available()) { stopCamera(); cameraInfo.setText("Home VPN is unavailable. Enable it and restart the camera."); }
        boolean ready = printer != null && printer.ready(), fresh = ready && printer.fresh(), writable = fresh && !printer.pinProbe(), busy = printer != null && printer.uploading(), connecting = printer != null && printer.connecting();
        snapshot = printer == null ? new JSONObject() : printer.status;
        connection.setText(printer == null ? "Preparing connection service…" : printer.connection);
        summary.setText(ready ? "Connected" + (printer.pinProbe() ? " · read-only PIN probe" : "") + " · " + printer.host() + " · " + (fresh ? StatusPresentation.state(snapshot) : "Status stale") : connecting ? "Connecting · " + printer.host() : "Disconnected · open Settings to connect");
        if (connecting && !printer.host().equals(host.getText().toString())) { host.setText(printer.host()); access.setText(""); }
        routePicker.setEnabled(!connecting); authPicker.setEnabled(!connecting); host.setEnabled(!connecting); access.setEnabled(!connecting); pairingPin.setEnabled(!connecting); serial.setEnabled(!connecting); remember.setEnabled(!connecting && !pinProbe());
        access.setVisibility(pinProbe() ? View.GONE : View.VISIBLE); pairingPin.setVisibility(pinProbe() ? View.VISIBLE : View.GONE); remember.setVisibility(pinProbe() ? View.GONE : View.VISIBLE);
        connect.setEnabled(printer != null); connect.setText(connecting ? "Disconnect" : "Connect"); check.setEnabled(!checking);
        refresh.setEnabled(ready); pause.setEnabled(writable && Cc2Codec.canPause(snapshot)); resume.setEnabled(writable && Cc2Codec.canResume(snapshot)); stop.setEnabled(writable && Cc2Codec.canStop(snapshot));
        discover.setEnabled(!remoteMode() && !connecting && !scanningNow); discover.setText(scanningNow ? "Scanning local Wi-Fi…" : "Find printers on Wi-Fi");
        saveProfile.setEnabled(!connecting); chooseProfile.setEnabled(!connecting); removeProfile.setEnabled(!connecting); profileName.setEnabled(!connecting);
        lightOn.setEnabled(writable); lightOff.setEnabled(writable); heater.setEnabled(writable && Cc2Codec.idle(snapshot)); fan.setEnabled(writable); speed.setEnabled(writable && Cc2Codec.canPause(snapshot));
        cancelUpload.setEnabled(busy); cancelDownload.setEnabled(printer != null && printer.downloading());
        renderFeatures(ready, fresh);
        refill.setEnabled(writable && printer.canvasFresh() && printer.canvas.has("auto_refill"));
        if (printer != null && printer.canvas != null && printer.canvas.has("auto_refill")) refill.setText(printer.canvas.optBoolean("auto_refill") ? "Disable automatic refill…" : "Enable automatic refill…");
        else refill.setText("Automatic refill unavailable");
        boolean fileBusy = printer != null && printer.fileBusy();
        upload.setEnabled(ready && !printer.pinProbe() && printer.selectedFile != null && !fileBusy); pick.setEnabled(printer != null && !fileBusy);
        boolean hasReport = printer != null && printer.selectedFile != null && printer.selectedReport != null;
        saveCopy.setEnabled(hasReport && !fileBusy); shareInspection.setEnabled(hasReport && !fileBusy); clearCopy.setEnabled(printer != null && printer.selectedFile != null && !fileBusy);
        GcodeInspector.Report report = hasReport ? printer.selectedReport : null;
        if (renderedReport != report || !hasReport) { renderedReport = report; inspection.setText(hasReport ? report.text() : "Select a G-code file to inspect it offline. Printer connection is not required."); }
        if (!hasReport && printer != null && printer.importing) inspection.setText("Importing and inspecting G-code…");
        filePreview.setImageBitmap(hasReport ? printer.selectedThumbnail : null); filePreview.setVisibility(hasReport && printer.selectedThumbnail != null ? View.VISIBLE : View.GONE);
        previewInfo.setText(!hasReport ? "Embedded previews appear when a supported image is present in the selected G-code." : report.thumbnail == null ? report.thumbnailNote : printer.selectedThumbnail == null ? "Embedded image found but Android could not decode it. File inspection remains available." : report.thumbnailNote + " · slicer image, not a live camera or motion simulation");
        materialDetails.setEnabled(hasReport && !report.materials.entries.isEmpty());
        selected.setText(printer != null && printer.selectedFile != null ? printer.selectedName + " · " + printer.selectedFile.length() / 1024 + " KiB" : "Choose a .gcode file sliced for this printer.");
        state.setText(!fresh && snapshot.length() > 0 ? "Status stale · controls disabled" : StatusPresentation.state(snapshot));
        JSONObject machine = snapshot.optJSONObject("machine_status"), print = snapshot.optJSONObject("print_status");
        int percent = machine == null ? 0 : machine.optInt("progress", 0); progress.setProgress(Math.max(0, Math.min(100, percent)));
        if (fresh && machine != null && machine.optInt("status", -1) == 2) state.append(" · " + percent + "%");
        temperatures.setText("Nozzle " + temperature("extruder") + "\nBed " + temperature("heater_bed") + "\nChamber " + temperature("ztemperature_sensor"));
        if (print != null && machine != null && machine.optInt("status", -1) == 2) {
            long remaining = print.optLong("remaining_time_sec", -1);
            job.setText(StatusPresentation.clean(print.optString("filename", "Current print")) + "\nLayer " + print.optInt("current_layer", 0) + " / " + print.optInt("total_layer", 0)
                + " · " + (remaining < 0 ? "Time unavailable" : remaining / 3600 + "h " + (remaining % 3600) / 60 + "m remaining"));
        } else job.setText(ready ? "No active print reported." : "Connect for live print status.");
        String codes = StatusPresentation.faultCodes(snapshot);
        faults.setText(snapshot.length() == 0 ? "Awaiting printer fault status." : codes.isEmpty() ? "No active fault codes reported." : "Printer reports fault code(s): " + codes + ". Check the printer screen for instructions.");
        faults.setTextColor(codes.isEmpty() ? MUTED : dark ? 0xffffa398 : 0xffa32929);
        trays.setText(printer == null ? "Connect for CANVAS tray status." : StatusPresentation.canvas(printer.canvas) + (printer.canvas != null && !printer.canvasFresh() ? "\nTray status is stale; refresh before changing refill." : ""));
        if (printer != null) {
            JSONObject version = printer.attributes.optJSONObject("software_version");
            identity.setText(StatusPresentation.clean(printer.attributes.optString("hostname", "CC2")) + " · " + StatusPresentation.clean(printer.attributes.optString("machine_model", "Centauri Carbon 2"))
                + (version == null ? "" : " · " + StatusPresentation.clean(version.optString("ota_version"))));
            feedback.setText(printer.feedback);
        }
    }
    private String temperature(String key) {
        JSONObject value = snapshot.optJSONObject(key); if (value == null || !value.has("temperature")) return "—";
        String text = String.format(Locale.ROOT, "%.1f°C", value.optDouble("temperature", 0));
        return value.has("target") ? text + String.format(Locale.ROOT, " / %.0f°C", value.optDouble("target", 0)) : text;
    }
    private void message(String text) { if (printer != null) printer.feedback = text; else pendingFeedback = text; if (feedback != null) feedback.setText(text); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == SAVE_GCODE) {
            if (result == RESULT_OK && data != null && data.getData() != null) { pendingExportUri = data.getData(); finishPendingExport(); }
            else { pendingExportHash = ""; pendingExportUri = null; }
        }
        if (request == PICK_FILE && result == RESULT_OK && data != null && data.getData() != null && printer != null) printer.select(data.getData());
        if (request == PICK_SNAPSHOT && result != RESULT_OK) pendingSnapshot = null;
        if (request == PICK_SNAPSHOT && result == RESULT_OK && data != null && data.getData() != null && pendingSnapshot != null) {
            Bitmap image = pendingSnapshot; pendingSnapshot = null; android.net.Uri uri = data.getData();
            checks.execute(() -> { String text; try (OutputStream output = getContentResolver().openOutputStream(uri)) { if (output == null || !image.compress(Bitmap.CompressFormat.JPEG, 92, output)) throw new IOException(); text = "Camera snapshot saved."; } catch (Exception error) { text = "Snapshot could not be saved. Capture it again."; } String done = text; main.post(() -> { if (!isDestroyed()) message(done); }); });
        }
    }
    private void finishPendingExport() {
        if (printer == null || pendingExportUri == null) return;
        printer.exportPhoneCopy(pendingExportUri, pendingExportHash); pendingExportUri = null; pendingExportHash = "";
    }
    @Override protected void onStart() { super.onStart(); active = true; if (printer != null) printer.observe(this::render); main.post(clock); }
    @Override protected void onStop() { active = false; stopCamera(); main.removeCallbacks(clock); if (printer != null) printer.observe(null); super.onStop(); }
    @Override protected void onDestroy() { if (scanning != null) scanning.close(); stopCamera(); if (printer != null) printer.observe(null); if (bound) unbindService(binding); checks.shutdownNow(); main.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("exportHash", pendingExportHash); out.putString("exportUri", pendingExportUri == null ? "" : pendingExportUri.toString()); out.putString("host", host.getText().toString()); out.putString("serial", serial.getText().toString()); out.putString("diagnostics", diagnostics.getText().toString()); out.putString("profileName", profileName.getText().toString()); out.putBoolean("remember", remember.isChecked()); out.putInt("page", page); }
    private static final class TransientInputs { String host, code; Bitmap snapshot; }
    @Override public Object onRetainNonConfigurationInstance() { TransientInputs state = new TransientInputs(); state.host = host.getText().toString(); state.code = access.getText().toString(); state.snapshot = pendingSnapshot; return state; }
    private void showLicenses() {
        try {
            StringBuilder text = new StringBuilder("Link Workshop v0.3.5\nIndependent Android app derived from Elegoo Link.\n\n");
            for (String name : new String[] {"THIRD_PARTY_NOTICES.md", "Apache-2.0.txt", "Paho-NOTICE.txt", "Paho-EDL-1.0.txt", "Paho-EPL-2.0.txt"}) {
                try (InputStream input = getAssets().open("licenses/" + name); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096]; int count; while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count); text.append(bytes.toString("UTF-8")).append("\n\n");
                }
            }
            ScrollView scroll = new ScrollView(this); TextView view = new TextView(this); view.setText(text.toString()); view.setTextColor(INK); view.setTextSize(13); view.setPadding(dp(20), dp(12), dp(20), dp(12)); scroll.addView(view);
            new AlertDialog.Builder(this).setTitle("About & licenses").setView(scroll).setPositiveButton("Close", null).show();
        } catch (IOException error) { feedback.setText("License information could not be opened."); }
    }
    private LinearLayout card(String title) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable background = new GradientDrawable(); background.setColor(SURFACE); background.setCornerRadius(dp(16)); card.setBackground(background);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2); layout.topMargin = dp(18); (currentSection == null ? content : currentSection).addView(card, layout); label(card, title, 18, INK, true); return card;
    }
    private TextView label(LinearLayout parent, String text, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(5), 0, dp(7)); parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private EditText input(LinearLayout parent, String hint, boolean password) {
        EditText input = new EditText(this); input.setHint(hint); input.setHintTextColor(MUTED); input.setBackgroundTintList(tint(TEAL)); input.setSingleLine(true); input.setTextColor(tint(INK)); if (password) input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        parent.addView(input, new LinearLayout.LayoutParams(-1, dp(56))); return input;
    }
    private Button button(LinearLayout parent, String text, Runnable action) {
        Button button = new Button(this); button.setText(text); styleButton(button);
        button.setOnClickListener(view -> action.run()); LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2); layout.topMargin = dp(6); parent.addView(button, layout); return button;
    }
    private ColorStateList tint(int color) { return new ColorStateList(new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}}, new int[] {MUTED, color}); }
    private void styleButton(Button button) {
        button.setAllCaps(false); button.setTextColor(tint(TEAL)); button.setMinHeight(dp(48));
        GradientDrawable shape = new GradientDrawable(); shape.setColor(BUTTON); shape.setCornerRadius(dp(10));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(dark ? 0x4463d5c7 : 0x33006b65), shape, null));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2); layout.topMargin = dp(6); button.setLayoutParams(layout); button.setPadding(dp(10), dp(8), dp(10), dp(8));
    }
    private CheckBox checkbox(LinearLayout parent, String text, boolean checked) { CheckBox view = new CheckBox(this); view.setText(text); view.setTextColor(INK); view.setButtonTintList(tint(TEAL)); view.setChecked(checked); parent.addView(view); return view; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
