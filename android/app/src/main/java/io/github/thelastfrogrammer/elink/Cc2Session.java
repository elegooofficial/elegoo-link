/* CC2 MQTT behavior derived from ELEGOO's Apache-2.0 adapter; Android port, 2026. */
package io.github.thelastfrogrammer.elink;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** One foreground connection. Never retries a printer-changing command. */
public final class Cc2Session implements AutoCloseable {
    public interface Listener {
        void connection(String message, boolean registered);
        void status(JSONObject snapshot);
        default void status(JSONObject snapshot, boolean canvasUpdated) { status(snapshot); }
        void result(String message);
        void uploadProgress(int percent);
        default void attributes(JSONObject attributes) { }
        default void canvas(JSONObject canvas) { }
        default void failure(String message, boolean retryable) { }
        default void query(int method, JSONObject params, JSONObject result) { }
        default void queryError(int method, String message) { }
        default void uploaded(String filename) { }
        default void downloadProgress(int percent) { }
        default void downloaded(File file, String filename) { }
    }
    interface MqttFactory { MqttClient create(String uri, String clientId) throws MqttException; }
    public interface IdentityResolver extends AutoCloseable {
        String resolve(PrinterHttp http) throws Exception;
        default String password(PrinterHttp http) throws Exception { return http.token(); }
        default String summary() { return ""; }
        default boolean readOnly() { return false; }
        default void close() { }
    }
    private volatile Listener listener;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService transfer = Executors.newSingleThreadExecutor();
    private final String clientId = "1_PC_" + (1000 + new java.security.SecureRandom().nextInt(9000));
    private final String requestId = clientId + "_req";
    private final PrinterHttp http;
    private final PrinterHttp.ConnectionFactory connections;
    private final javax.net.SocketFactory sockets;
    private final MqttFactory clients;
    private final IdentityResolver identity;
    private final Cc2Codec codec = new Cc2Codec();
    private final AtomicInteger ids = new AtomicInteger(1);
    private final Map<Integer, Pending> pending = new HashMap<>(); // worker thread only
    private final Set<Integer> queued = new HashSet<>();
    private long nextRequestAt;
    private final Deque<JSONObject> requestQueue = new ArrayDeque<>();
    private ScheduledFuture<?> dispatch;
    private volatile boolean uploadCancelled;
    private final CompletableFuture<JSONObject> registration = new CompletableFuture<>();
    private final RegistrationDiagnostics registrationFacts = new RegistrationDiagnostics();
    private volatile MqttClient mqtt;
    private volatile PrinterHttp uploadHttp, downloadHttp;
    private volatile boolean closed, ready, uploading, downloading, downloadCancelled;
    private volatile String sessionToken;
    private boolean canvasSupported = true;
    private volatile long statusAt;
    private long canvasAt;
    private String base;
    private static final class Pending {
        final int method; final long deadline; final JSONObject params;
        Pending(int method, JSONObject params) { this.method = method; this.params = params; deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Cc2Codec.isQuery(method) ? 15 : 8); }
    }
    public Cc2Session(String host, String accessCode, Listener listener) {
        this(host, accessCode, listener, url -> (java.net.HttpURLConnection) url.openConnection(), null);
    }
    public Cc2Session(String host, String accessCode, Listener listener, PrinterHttp.ConnectionFactory connections, javax.net.SocketFactory sockets) {
        this(host, accessCode, listener, connections, sockets, new PrinterIdentity("", new Cc2Discovery()));
    }
    public Cc2Session(String host, String accessCode, Listener listener, PrinterHttp.ConnectionFactory connections, javax.net.SocketFactory sockets, IdentityResolver identity) {
        this(host, accessCode, listener, connections, sockets, (uri, id) -> new MqttClient(uri, id, new MemoryPersistence()), identity);
    }
    Cc2Session(String host, String accessCode, Listener listener, PrinterHttp.ConnectionFactory connections, javax.net.SocketFactory sockets, MqttFactory clients) {
        this(host, accessCode, listener, connections, sockets, clients, http -> http.systemInfo().getString("sn"));
    }
    Cc2Session(String host, String accessCode, Listener listener, PrinterHttp.ConnectionFactory connections, javax.net.SocketFactory sockets, MqttFactory clients, IdentityResolver identity) {
        http = new PrinterHttp(host, identity.readOnly() ? "" : accessCode, connections); this.connections = connections; this.sockets = sockets;
        this.clients = clients; this.listener = listener; this.identity = identity; sessionToken = http.token();
    }
    public boolean ready() { return ready && !closed; }
    public boolean fresh() { return ready() && statusAt != 0 && System.nanoTime() - statusAt < TimeUnit.SECONDS.toNanos(20); }
    public boolean uploading() { return uploading; }
    public boolean downloading() { return downloading; }
    public boolean readOnly() { return identity.readOnly(); }
    public void connect() { execute(this::connectOnWorker); }
    private void connectOnWorker() {
        String stage = "Printer identity";
        try {
            emitConnection("Identifying printer for MQTT…", false);
            String serial = identity.resolve(http);
            if (!Cc2Discovery.validSerial(serial)) throw new PrinterErrors.IdentityUnavailable();
            if (closed) return;
            if (!identity.summary().isEmpty()) emitResult(identity.summary());
            sessionToken = identity.password(http);
            base = "elegoo/" + serial + "/";
            stage = "MQTT (port 1883)";
            MqttClient client = clients.create("tcp://" + http.host() + ":1883", clientId);
            mqtt = client;
            client.setTimeToWait(8000);
            client.setCallback(new MqttCallback() {
                public void connectionLost(Throwable cause) {
                    registration.completeExceptionally(new IllegalStateException("MQTT connection lost"));
                    execute(() -> fail("MQTT connection lost. Reconnecting requires a new registration.", true));
                }
                public void deliveryComplete(IMqttDeliveryToken token) { }
                public void messageArrived(String topic, MqttMessage message) {
                    if (closed) return;
                    if (message.getPayload().length > 2 * 1024 * 1024) { execute(() -> fail("Printer message too large", false)); return; }
                    boolean registerReply = topic.equals(base + requestId + "/register_response");
                    boolean registering = !ready && !registration.isDone();
                    if (registerReply && message.isRetained()) { if (registering) registrationFacts.retained(); return; }
                    try {
                        JSONObject payload = new JSONObject(new String(message.getPayload(), StandardCharsets.UTF_8));
                        if (registerReply) {
                            if (registering && registrationFacts.response(payload, clientId)) registration.complete(payload);
                        } else {
                            if (registering) {
                                if (topic.equals(base + "api_status")) registrationFacts.status();
                                else if (topic.equals(base + clientId + "/api_response")) registrationFacts.api();
                                else registrationFacts.other();
                            }
                            execute(() -> handle(topic, payload));
                        }
                    } catch (org.json.JSONException ignored) {
                        if (registering && registerReply) registrationFacts.malformed();
                        // Never log credentials, topics or payloads.
                    }
                }
            });
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName("elegoo"); options.setPassword(sessionToken.toCharArray());
            options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
            options.setCleanSession(true); options.setAutomaticReconnect(false);
            options.setConnectionTimeout(5); options.setKeepAliveInterval(20);
            if (sockets != null) options.setSocketFactory(sockets);
            emitConnection("Connecting…", false);
            client.connect(options);
            if (closed) return;
            registrationFacts.brokerAccepted();
            stage = "MQTT subscriptions";
            emitConnection("Registering with printer…", false);
            registrationFacts.subscribing();
            IMqttToken subscription = client.subscribeWithResponse(new String[] {base + clientId + "/api_response", base + "api_status", base + requestId + "/register_response"}, new int[] {1, 1, 1});
            if (!registrationFacts.subscribed(subscription == null ? null : subscription.getGrantedQos())) {
                fail("MQTT subscription acknowledgement was rejected or invalid. Registration was not sent.\n" + registrationFacts.report(), false); return;
            }
            stage = "MQTT registration publish";
            // Match the SDK's QoS 1 registration. Synchronous Paho publish waits for PUBACK.
            client.publish(base + "api_register", new JSONObject().put("client_id", clientId).put("request_id", requestId).toString().getBytes(StandardCharsets.UTF_8), 1, false);
            registrationFacts.published();
            stage = "Printer registration";
            JSONObject reply = registration.get(8, TimeUnit.SECONDS);
            if (!Cc2Codec.validRegistration(reply, clientId)) {
                String reason = reply.optString("error", "").toLowerCase(Locale.ROOT).contains("too many clients") ? "Printer connection limit reached." : "Printer registration rejected.";
                fail(reason + (readOnly() ? " PIN probe stopped; this app did not request other clients to disconnect. Keep Matrix/cloud mode enabled. Firmware may not support local PIN access." : " Check the selected authentication mode and other connected clients.") + "\n" + registrationFacts.report(), false); return;
            }
            if (closed) return;
            ready = true;
            emitConnection(readOnly() ? "Connected · read-only PIN probe; verify Matrix separately" : "Connected on local Wi-Fi", true);
            send(Cc2Codec.ATTRIBUTES); send(Cc2Codec.STATUS); send(Cc2Codec.CANVAS);
            worker.scheduleWithFixedDelay(() -> {
                if (!ready()) return;
                try { publish(base + clientId + "/api_request", new JSONObject().put("type", "PING")); }
                catch (Exception exception) { fail("Printer heartbeat failed.", true); }
            }, 10, 10, TimeUnit.SECONDS);
            worker.scheduleWithFixedDelay(() -> {
                if (!ready()) return;
                if (pending.values().stream().noneMatch(p -> p.method == Cc2Codec.STATUS)) send(Cc2Codec.STATUS);
            }, 15, 15, TimeUnit.SECONDS);
            worker.scheduleWithFixedDelay(() -> { if (ready() && canvasSupported && pending.values().stream().noneMatch(p -> p.method == Cc2Codec.CANVAS)) send(Cc2Codec.CANVAS); }, 30, 30, TimeUnit.SECONDS);
            worker.scheduleWithFixedDelay(this::expireRequests, 1, 1, TimeUnit.SECONDS);
        } catch (Exception exception) {
            String text = PrinterErrors.describe(exception, stage);
            if (readOnly() && exception instanceof MqttException && (((MqttException) exception).getReasonCode() == 4 || ((MqttException) exception).getReasonCode() == 5)) text = "Cloud-mode local PIN was not authorized (MQTT code " + ((MqttException) exception).getReasonCode() + "). Verify the current printer-displayed PIN. Keep Matrix/cloud mode enabled; firmware may not permit this local path. No other credentials were tried.";
            if (!closed) fail(text + (exception instanceof MqttException && !identity.summary().isEmpty() ? "\n" + identity.summary() : "")
                + (stage.equals("MQTT subscriptions") || stage.equals("MQTT registration publish") || stage.equals("Printer registration") ? "\n" + registrationFacts.report() : ""), !readOnly() && PrinterErrors.retryable(exception));
        } finally { if (closed) disposeMqtt(); }
    }
    public void refresh() { execute(() -> { if (ready()) { send(Cc2Codec.STATUS); if (canvasSupported) send(Cc2Codec.CANVAS); } }); }
    public void autoRefill(boolean enabled) {
        execute(() -> {
            if (!fresh()) { emitResult("Refresh printer status before changing automatic refill."); return; }
            if (pending.values().stream().anyMatch(p -> p.method == Cc2Codec.AUTO_REFILL)) { emitResult("Waiting for automatic-refill acknowledgement."); return; }
            try { send(Cc2Codec.autoRefillRequest(ids.getAndIncrement(), enabled)); }
            catch (Exception exception) { emitResult("Could not send automatic-refill request."); }
        });
    }
    public void command(int method) {
        execute(() -> {
            try {
                JSONObject snapshot = codec.snapshot();
                if (!fresh() || (method == Cc2Codec.PAUSE ? !Cc2Codec.canPause(snapshot) : method == Cc2Codec.RESUME ? !Cc2Codec.canResume(snapshot) : method != Cc2Codec.STOP || !Cc2Codec.canStop(snapshot))) {
                    emitResult("Control unavailable. Refresh the printer status first."); return;
                }
                if (pending.values().stream().anyMatch(p -> changing(p.method)) || queued.stream().anyMatch(Cc2Session::changing)) {
                    emitResult("Waiting for the previous command response."); return;
                }
                send(method);
            } catch (Exception exception) { emitResult(error(exception)); }
        });
    }
    public void files(String storage, int offset) { execute(() -> prepare(() -> Cc2Codec.filesRequest(ids.getAndIncrement(), storage, offset))); }
    public void history() { execute(() -> send(Cc2Codec.HISTORY)); }
    public void disk() { execute(() -> send(Cc2Codec.DISK)); }
    public void camera() { execute(() -> send(Cc2Codec.CAMERA)); }
    public void delete(String storage, String filename) { execute(() -> prepare(() -> Cc2Codec.deleteRequest(ids.getAndIncrement(), storage, filename))); }
    public void start(String storage, String filename, boolean leveling, boolean force, boolean timelapse, String plate, org.json.JSONArray maps) {
        execute(() -> prepare(() -> Cc2Codec.startRequest(ids.getAndIncrement(), storage, filename, leveling, force, timelapse, plate, maps)));
    }
    public void light(boolean on) { execute(() -> prepare(() -> Cc2Codec.lightRequest(ids.getAndIncrement(), on))); }
    public void temperatures(int nozzle, int bed) { execute(() -> prepare(() -> Cc2Codec.temperatureRequest(ids.getAndIncrement(), nozzle, bed))); }
    public void fan(String name, int percent) { execute(() -> prepare(() -> Cc2Codec.fanRequest(ids.getAndIncrement(), name, percent))); }
    public void speed(int mode) { execute(() -> prepare(() -> Cc2Codec.speedRequest(ids.getAndIncrement(), mode))); }
    private interface Prepared { JSONObject get() throws Exception; }
    private void prepare(Prepared request) { try { send(request.get()); } catch (Exception error) { emitResult("Invalid request. Check the selected file and settings."); } }
    private void send(int method) {
        if (!ready()) return;
        int id = ids.getAndIncrement();
        try { send(Cc2Codec.request(id, method)); }
        catch (Exception exception) { emitResult("Could not prepare printer request."); }
    }
    private void send(JSONObject message) {
        int id = message.optInt("id"), method = message.optInt("method");
        if (!ready()) return;
        if (readOnly() && changing(method)) { emitResult("Read-only PIN probe: printer-changing commands are disabled."); return; }
        if (queued.contains(method) || pending.values().stream().anyMatch(p -> p.method == method)
            || changing(method) && (queued.stream().anyMatch(Cc2Session::changing) || pending.values().stream().anyMatch(p -> changing(p.method)))) {
            emitResult("Waiting for the previous request to finish."); return;
        }
        queued.add(method);
        // Stop takes the next available slot ahead of reads; all requests retain the firmware spacing.
        if (method == Cc2Codec.STOP) requestQueue.addFirst(message); else requestQueue.addLast(message);
        scheduleDispatch();
    }
    private void scheduleDispatch() {
        if (dispatch != null || requestQueue.isEmpty() || !ready()) return;
        dispatch = worker.schedule(() -> {
            dispatch = null;
            if (!ready() || requestQueue.isEmpty()) return;
            JSONObject message = requestQueue.removeFirst(); int method = message.optInt("method");
            queued.remove(method);
            if (!allowed(message)) emitResult("Control unavailable. Refresh status and check the printer state before trying again.");
            else { nextRequestAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(2); transmit(message.optInt("id"), method, message); }
            scheduleDispatch();
        }, Math.max(0, nextRequestAt - System.nanoTime()), TimeUnit.NANOSECONDS);
    }
    private boolean allowed(JSONObject message) {
        int method = message.optInt("method");
        if (readOnly() && changing(method)) return false;
        if (!changing(method)) return true;
        if (!fresh()) return false;
        try {
            JSONObject status = codec.snapshot();
            if (method == Cc2Codec.PAUSE) return Cc2Codec.canPause(status);
            if (method == Cc2Codec.RESUME) return Cc2Codec.canResume(status);
            if (method == Cc2Codec.STOP) return Cc2Codec.canStop(status);
            if (method == Cc2Codec.START) {
                org.json.JSONArray maps = message.getJSONObject("params").getJSONObject("config").getJSONArray("slot_map");
                return Cc2Codec.idle(status) && StatusPresentation.faultCodes(status).isEmpty()
                    && (maps.length() == 0 || canvasAt != 0 && System.nanoTime() - canvasAt < TimeUnit.SECONDS.toNanos(45)
                        && FeatureData.mappings(status.optJSONObject("canvas_info"), maps));
            }
            if (method == Cc2Codec.DELETE || method == Cc2Codec.TEMPERATURE) return Cc2Codec.idle(status) && StatusPresentation.faultCodes(status).isEmpty();
            if (method == Cc2Codec.AUTO_REFILL) return canvasAt != 0 && System.nanoTime() - canvasAt < TimeUnit.SECONDS.toNanos(45);
            if (method == Cc2Codec.SPEED) return Cc2Codec.canPause(status);
            return true;
        } catch (Exception error) { return false; }
    }
    private void transmit(int id, int method, JSONObject message) {
        try {
            pending.put(id, new Pending(method, message.getJSONObject("params")));
            publish(base + clientId + "/api_request", message);
            if (changing(method)) emitResult("Command sent; waiting for printer acknowledgement…");
        } catch (Exception exception) {
            pending.remove(id);
            if (Cc2Codec.isQuery(method)) { Listener current = listener; if (current != null) current.queryError(method, "Request could not be sent. Refresh to retry."); }
            else emitResult("Request failed; outcome unknown. Refresh before trying again.");
        }
    }
    private void publish(String topic, JSONObject payload) throws Exception {
        if (closed) throw new IllegalStateException("Session closed");
        mqtt.publish(topic, payload.toString().getBytes(StandardCharsets.UTF_8), 0, false);
    }
    private void handle(String topic, JSONObject message) {
        if (!ready()) return;
        try {
            boolean response = topic.equals(base + clientId + "/api_response");
            int method = message.optInt("method", -1);
            if (response) {
                Pending request = pending.get(message.optInt("id", -1));
                if (request == null || method != request.method) return;
                pending.remove(message.getInt("id"));
                JSONObject result = message.optJSONObject("result");
                boolean query = Cc2Codec.isQuery(method);
                if (result == null || result.has("error_code") && result.optInt("error_code", -1) != 0
                    || !result.has("error_code") && !(query && Cc2Codec.queryShape(method, result))) {
                    int code = result == null ? -1 : result.optInt("error_code", -1);
                    if (code == 1000) { fail(readOnly() ? "Printer rejected authorization for the PIN probe (code 1000). Keep Matrix/cloud mode enabled; this local path may be unsupported." : PrinterErrors.code(code), false); return; }
                    if (method == Cc2Codec.CANVAS && code == 1001) canvasSupported = false;
                    emitResult(PrinterErrors.code(code)); Listener current = listener;
                    if (query && current != null) current.queryError(method, PrinterErrors.code(code)); return;
                }
                if (query) {
                    Listener current = listener;
                    if (current != null) {
                        if (Cc2Codec.queryShape(method, result)) current.query(method, request.params, result);
                        else current.queryError(method, "Printer returned unexpected data for this feature.");
                    }
                    return;
                }
                if (method == Cc2Codec.DELETE) { Listener current = listener; if (current != null) current.query(method, request.params, result); emitResult("File deletion acknowledged. Refreshing files."); return; }
                if (method == Cc2Codec.AUTO_REFILL) { emitResult("Automatic refill acknowledged. Refreshing tray state."); send(Cc2Codec.CANVAS); return; }
                Listener current = listener;
                if (method == Cc2Codec.ATTRIBUTES && current != null) current.attributes(result);
                if (method == Cc2Codec.CANVAS && result.optJSONObject("canvas_info") != null) {
                    canvasAt = System.nanoTime();
                    codec.canvas(result.getJSONObject("canvas_info"));
                    if (current != null) current.canvas(result.getJSONObject("canvas_info"));
                }
                if (changing(method)) {
                    emitResult("Printer acknowledged " + methodName(method) + ". Waiting for status update.");
                    send(Cc2Codec.STATUS); return;
                }
            } else if (!topic.equals(base + "api_status") || method != 6000) return;
            if (method == Cc2Codec.STATUS || method == 6000) {
                if (codec.accept(message)) {
                    if (message.getJSONObject("result").has("canvas_info")) canvasAt = System.nanoTime();
                    statusAt = System.nanoTime();
                    Listener current = listener; if (current != null) current.status(codec.snapshot(), message.getJSONObject("result").has("canvas_info"));
                } else if (pending.values().stream().noneMatch(p -> p.method == Cc2Codec.STATUS)) {
                    statusAt = 0; send(Cc2Codec.STATUS);
                }
            }
        } catch (Exception exception) { emitResult("Could not parse printer response. Refresh status."); }
    }
    private void expireRequests() {
        Iterator<Pending> iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            Pending request = iterator.next();
            if (System.nanoTime() <= request.deadline) continue;
            iterator.remove();
            if (changing(request.method))
                emitResult("No command acknowledgement. Outcome unknown; refresh before trying again.");
            else if (request.method == Cc2Codec.STATUS) { statusAt = 0; emitResult("Status request timed out. Controls disabled until fresh status arrives."); }
            else if (Cc2Codec.isQuery(request.method)) { Listener current = listener; if (current != null) current.queryError(request.method, "No response. This feature may be unavailable on the current firmware; refresh to retry."); }
        }
    }
    public synchronized void upload(File file, String name) {
        if (readOnly()) { emitResult("Read-only PIN probe: uploads are disabled; PINs are never used as HTTP tokens."); return; }
        if (!ready() || uploading || downloading) return;
        uploading = true; uploadCancelled = false;
        PrinterHttp uploader = new PrinterHttp(http.host(), sessionToken, connections); uploadHttp = uploader;
        transfer.execute(() -> {
            try {
                uploader.upload(file, name, percent -> { Listener current = listener; if (current != null && !closed) current.uploadProgress(percent); });
                emitResult("Upload acknowledged by printer. Refresh Files to review print setup.");
                Listener current = listener; if (current != null && !closed) current.uploaded(name);
            } catch (Exception exception) { if (!closed && !uploadCancelled) emitResult(PrinterErrors.describe(exception, "Upload")); }
            finally { uploading = false; uploadHttp = null; }
        });
    }
    public void cancelUpload() { PrinterHttp uploader = uploadHttp; if (uploader != null) { uploadCancelled = true; uploader.cancel(); emitResult("Upload cancelled. A partial file may remain on the printer; refresh files before trying again."); } }
    public synchronized boolean download(File destination, String storage, String name) {
        if (readOnly()) { emitResult("PIN probe cannot download files: PINs are not HTTP tokens."); return false; }
        if (!ready() || uploading || downloading) return false;
        Cc2Codec.storage(storage); Cc2Codec.filename(name);
        downloading = true; downloadCancelled = false;
        PrinterHttp downloader = new PrinterHttp(http.host(), sessionToken, connections); downloadHttp = downloader;
        try {
            transfer.execute(() -> {
                boolean delivered = false;
                try {
                    downloader.download(destination, storage, name, percent -> { Listener current = listener; if (current != null && !closed && !downloadCancelled) current.downloadProgress(percent); });
                    Listener current = listener;
                    if (current != null && !closed && !downloadCancelled) { current.downloaded(destination, name); delivered = true; }
                } catch (Exception exception) { if (!closed && !downloadCancelled) emitResult(PrinterErrors.describe(exception, "File download")); }
                finally { if (!delivered) destination.delete(); downloading = false; downloadHttp = null; }
            });
            return true;
        } catch (RejectedExecutionException stopped) { downloading = false; downloadHttp = null; destination.delete(); return false; }
    }
    public void cancelDownload() { PrinterHttp downloader = downloadHttp; if (downloader != null) { downloadCancelled = true; downloader.cancel(); emitResult("Download cancelled. Partial phone copy will be removed; printer files are unchanged."); } }
    private void execute(Runnable task) { if (!closed) try { worker.execute(() -> { if (!closed) task.run(); }); } catch (RejectedExecutionException ignored) { } }
    private void emitConnection(String message, boolean connected) { Listener current = listener; if (current != null && !closed) current.connection(message, connected); }
    private void emitResult(String message) { Listener current = listener; if (current != null && !closed) current.result(message); }
    private void fail(String message, boolean retryable) {
        ready = false; statusAt = 0;
        if (pending.values().stream().anyMatch(p -> changing(p.method))) emitResult("Connection lost with a command pending. Outcome unknown; commands will not be replayed.");
        boolean canRetry = !readOnly() && retryable;
        Listener current = listener; close(); if (current != null) current.failure(message, canRetry);
    }
    private static boolean changing(int method) { return Cc2Codec.changing(method); }
    private static String methodName(int method) {
        if (method == Cc2Codec.START) return "print start"; if (method == Cc2Codec.PAUSE) return "pause";
        if (method == Cc2Codec.STOP) return "stop"; if (method == Cc2Codec.RESUME) return "resume";
        if (method == Cc2Codec.LIGHT) return "light setting"; if (method == Cc2Codec.TEMPERATURE) return "temperature targets";
        if (method == Cc2Codec.FAN) return "fan setting"; if (method == Cc2Codec.SPEED) return "speed mode";
        return "setting";
    }
    private static String error(Exception exception) {
        if (exception instanceof TimeoutException) return "Printer registration timed out";
        if (exception instanceof MqttException) return "MQTT connection failed (code " + ((MqttException) exception).getReasonCode() + "). Check LAN mode and access code.";
        return PrinterErrors.describe(exception, "Printer request");
    }
    private void disposeMqtt() {
        MqttClient current = mqtt;
        if (current == null) return;
        try { if (current.isConnected()) current.disconnectForcibly(0, 500, false); current.close(true); } catch (Exception ignored) { }
    }
    public void close() {
        if (closed) return;
        closed = true; ready = false; listener = null;
        registration.completeExceptionally(new IllegalStateException("Session closed"));
        try { identity.close(); } catch (Exception ignored) { }
        http.cancel(); PrinterHttp uploader = uploadHttp; if (uploader != null) uploader.cancel();
        PrinterHttp downloader = downloadHttp; if (downloader != null) downloader.cancel();
        worker.shutdownNow(); transfer.shutdownNow();
        Thread cleanup = new Thread(this::disposeMqtt, "mqtt-cleanup"); cleanup.setDaemon(true); cleanup.start();
    }
}
