package io.github.thelastfrogrammer.elink;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Runs the production session through fake HTTP/MQTT transports, not a duplicated session model. */
public class Cc2SessionTest {
    private static final class Listener implements Cc2Session.Listener {
        final BlockingQueue<File> downloads = new LinkedBlockingQueue<>();
        public void downloaded(File file, String name) { downloads.add(file); }
        final BlockingQueue<JSONObject> statuses = new LinkedBlockingQueue<>(), canvases = new LinkedBlockingQueue<>(), attrs = new LinkedBlockingQueue<>();
        final BlockingQueue<String> results = new LinkedBlockingQueue<>(), failures = new LinkedBlockingQueue<>();
        final BlockingQueue<JSONObject> queries = new LinkedBlockingQueue<>();
        final BlockingQueue<String> queryErrors = new LinkedBlockingQueue<>();
        public void query(int method, JSONObject params, JSONObject result) { try { queries.add(new JSONObject().put("method",method).put("params",params).put("result",result)); } catch (Exception error) { throw new AssertionError(error); } }
        public void queryError(int method,String message) { queryErrors.add(method+":"+message); }
        volatile boolean retryable;
        public void connection(String message, boolean registered) { }
        public void status(JSONObject snapshot) { statuses.add(snapshot); }
        public void result(String message) { results.add(message); }
        public void uploadProgress(int percent) { }
        public void attributes(JSONObject data) { attrs.add(data); }
        public void canvas(JSONObject data) { canvases.add(data); }
        public void failure(String message, boolean canRetry) { retryable = canRetry; failures.add(message); }
    }
    private static HttpURLConnection info(URL url, String response) {
        return new HttpURLConnection(url) {
            public void connect() { }
            public void disconnect() { }
            public boolean usingProxy() { return false; }
            public int getResponseCode() { return 200; }
            public InputStream getInputStream() { return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)); }
        };
    }
    private static class FakeMqtt extends MqttClient {
        MqttCallback callback;
        String responseTopic;
        volatile boolean connected;
        String password;
        volatile int machine = 2, sub = 2075;
        volatile boolean rejectCamera, failDisk;
        final List<String> subscriptions = new CopyOnWriteArrayList<>();
        final BlockingQueue<JSONObject> writes = new LinkedBlockingQueue<>();
        final List<JSONObject> requests = new CopyOnWriteArrayList<>();
        FakeMqtt(String uri, String id) throws MqttException { super(uri, id, new MemoryPersistence()); }
        @Override public void setCallback(MqttCallback callback) { this.callback = callback; }
        @Override public void connect(MqttConnectOptions options) throws MqttException {
            assertEquals("elegoo", options.getUserName()); assertFalse(options.isAutomaticReconnect()); password = new String(options.getPassword()); connected = true;
            assertEquals(MqttConnectOptions.MQTT_VERSION_3_1_1, options.getMqttVersion());
        }
        @Override public IMqttToken subscribeWithResponse(String[] topics, int[] qos) {
            assertArrayEquals(new int[] {1,1,1}, qos);
            subscriptions.addAll(Arrays.asList(topics)); responseTopic = topics[0];
            return new MqttToken() { @Override public int[] getGrantedQos() { return new int[] {1,1,1}; } };
        }
        @Override public boolean isConnected() { return connected; }
        @Override public void disconnectForcibly(long quiesce, long timeout, boolean packet) { connected = false; }
        @Override public void close(boolean force) { connected = false; }
        @Override public void publish(String topic, byte[] bytes, int qos, boolean retained) throws MqttException {
            try {
                JSONObject request = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                if (topic.endsWith("api_register")) {
                    assertEquals(1, qos); assertFalse(retained);
                    callback.messageArrived(subscriptions.get(2), new MqttMessage(new JSONObject().put("client_id", request.getString("client_id")).put("error", "ok").toString().getBytes(StandardCharsets.UTF_8)));
                    return;
                }
                if (!request.has("method")) return;
                requests.add(request); int method = request.getInt("method");
                if (Cc2Codec.changing(method)) { writes.add(request); return; }
                JSONObject result = new JSONObject().put("error_code", 0);
                if (method == Cc2Codec.STATUS) result.put("machine_status", new JSONObject().put("status", machine).put("sub_status", sub));
                if (method == Cc2Codec.CANVAS) result.put("canvas_info", new JSONObject().put("auto_refill", false));
                if (method == Cc2Codec.ATTRIBUTES) result.put("hostname", "Test CC2");
                if (method == Cc2Codec.FILES) result = new JSONObject().put("file_list",new org.json.JSONArray().put(new JSONObject().put("filename","test.gcode"))).put("total",51);
                if (method == Cc2Codec.CAMERA) result = rejectCamera ? new JSONObject().put("error_code",1001) : new JSONObject().put("url","http://192.168.1.50:8080/?action=stream");
                if (method == Cc2Codec.HISTORY) result = new JSONObject().put("history_task_list",new org.json.JSONArray());
                if (method == Cc2Codec.DISK) { if (failDisk) throw new IOException("publish failed"); result = new JSONObject().put("total_bytes",1000).put("used_bytes",500); }
                response(request.getInt("id"), method, result);
            } catch (Exception error) { throw new MqttException(error); }
        }
        void response(int id, int method, JSONObject result) throws Exception {
            JSONObject reply = new JSONObject().put("id", id).put("method", method).put("result", result);
            callback.messageArrived(responseTopic, new MqttMessage(reply.toString().getBytes(StandardCharsets.UTF_8)));
        }
    }
    private static <T> T take(BlockingQueue<T> queue) throws Exception {
        T value = queue.poll(12, TimeUnit.SECONDS); assertNotNull("Expected callback", value); return value;
    }
    @Test public void connectsRegistersAndDeliversAttributesStatusAndCanvas() throws Exception {
        Listener listener = new Listener(); List<FakeMqtt> clients = new CopyOnWriteArrayList<>();
        Cc2Session session = new Cc2Session("192.168.1.50", "code", listener,
            url -> info(url, "{\"system_info\":{\"sn\":\"TEST-SERIAL\"}}"), null,
            (uri, id) -> { FakeMqtt fake = new FakeMqtt(uri, id); clients.add(fake); return fake; });
        try {
            session.connect(); assertEquals("Test CC2", take(listener.attrs).getString("hostname"));
            assertTrue(Cc2Codec.canPause(take(listener.statuses))); assertFalse(take(listener.canvases).getBoolean("auto_refill"));
            assertTrue(session.ready()); assertTrue(session.fresh());
            assertEquals(3, clients.get(0).subscriptions.size()); assertTrue(clients.get(0).subscriptions.get(0).startsWith("elegoo/TEST-SERIAL/"));
        } finally { session.close(); }
    }
    @Test public void wrongAcknowledgementCannotCompletePauseAndPendingWriteIsNotReplayed() throws Exception {
        Listener listener = new Listener(); List<FakeMqtt> clients = new CopyOnWriteArrayList<>();
        Cc2Session session = new Cc2Session("192.168.1.50", "code", listener,
            url -> info(url, "{\"system_info\":{\"sn\":\"TEST\"}}"), null,
            (uri, id) -> { FakeMqtt fake = new FakeMqtt(uri, id); clients.add(fake); return fake; });
        try {
            session.connect(); take(listener.statuses); FakeMqtt client = clients.get(0);
            session.command(Cc2Codec.PAUSE); JSONObject pause = take(client.writes); take(listener.results);
            client.response(pause.getInt("id") + 100, Cc2Codec.PAUSE, new JSONObject().put("error_code", 0));
            session.refresh(); take(listener.statuses); assertTrue(listener.results.isEmpty());
            client.callback.connectionLost(new IOException("wifi lost"));
            assertTrue(take(listener.results).contains("will not be replayed")); assertTrue(take(listener.failures).contains("connection lost"));
            assertFalse(session.ready()); assertTrue(listener.retryable);
            assertEquals(1, client.requests.stream().filter(r -> r.optInt("method") == Cc2Codec.PAUSE).count());
            assertTrue(client.writes.isEmpty());
        } finally { session.close(); }
    }
    @Test public void rejectedHttpCredentialNeverOpensMqttAndIsNotRetryable() throws Exception {
        Listener listener = new Listener();
        Cc2Session session = new Cc2Session("192.168.1.50", "secret", listener,
            url -> info(url, "{\"error_code\":1000}"), null,
            (uri, id) -> { fail("MQTT must not start after rejected HTTP authentication"); return null; });
        try {
            session.connect(); assertTrue(take(listener.failures).contains("Access code rejected")); assertFalse(listener.retryable); assertFalse(session.ready());
        } finally { session.close(); }
    }
    @Test public void refillUsesExplicitPayloadAndMatchingAcknowledgement() throws Exception {
        Listener listener = new Listener(); List<FakeMqtt> clients = new CopyOnWriteArrayList<>();
        Cc2Session session = new Cc2Session("192.168.1.50", "code", listener,
            url -> info(url, "{\"system_info\":{\"sn\":\"TEST\"}}"), null,
            (uri, id) -> { FakeMqtt fake = new FakeMqtt(uri, id); clients.add(fake); return fake; });
        try {
            session.connect(); take(listener.statuses); take(listener.canvases);
            session.autoRefill(true); JSONObject refill = take(clients.get(0).writes);
            assertEquals(2004, refill.getInt("method")); assertTrue(refill.getJSONObject("params").getBoolean("auto_refill")); take(listener.results);
            clients.get(0).response(refill.getInt("id"), 2004, new JSONObject().put("error_code", 0));
            assertTrue(take(listener.results).contains("acknowledged")); take(listener.canvases);
        } finally { session.close(); }
    }
    @Test public void discoveredIdentityRegistersAndReceivesStatusWithoutOpeningHttp() throws Exception {
        Listener listener = new Listener(); List<FakeMqtt> clients = new CopyOnWriteArrayList<>();
        Cc2Session session = new Cc2Session("192.168.1.84", "actual-code", listener,
            url -> { fail("Monitoring must not require HTTP when identity is available"); return null; }, null,
            (uri, id) -> { FakeMqtt fake = new FakeMqtt(uri, id); clients.add(fake); return fake; }, http -> "DISCOVERED-CC2");
        try {
            session.connect(); take(listener.statuses); assertTrue(session.ready());
            assertEquals("actual-code", clients.get(0).password);
            assertTrue(clients.get(0).subscriptions.stream().allMatch(topic -> topic.startsWith("elegoo/DISCOVERED-CC2/")));
            session.command(Cc2Codec.PAUSE); assertEquals(Cc2Codec.PAUSE, take(clients.get(0).writes).getInt("method"));
        } finally { session.close(); }
    }
    @Test public void manualIdentityStillRequiresMqttAuthentication() throws Exception {
        Listener listener = new Listener();
        Cc2Session session = new Cc2Session("192.168.1.84", "wrong-code", listener,
            url -> { fail("HTTP must not open"); return null; }, null,
            (uri, id) -> new FakeMqtt(uri, id) {
                @Override public void connect(MqttConnectOptions options) throws MqttSecurityException { throw new MqttSecurityException(5); }
            }, new PrinterIdentity("MANUAL-CC2", new Cc2Discovery(() -> { throw new IOException("UDP blocked"); }, Collections.emptyList())));
        try { session.connect(); assertTrue(take(listener.failures).contains("not authorized")); assertFalse(listener.retryable); assertFalse(session.ready()); }
        finally { session.close(); }
    }
    @Test public void invalidIdentityCannotBecomeAMqttTopic() throws Exception {
        Listener listener = new Listener();
        Cc2Session session = new Cc2Session("192.168.1.84", "code", listener,
            url -> { fail("HTTP must not open"); return null; }, null,
            (uri, id) -> { fail("Invalid identity must not open MQTT"); return null; }, http -> "#/wrong");
        try { session.connect(); assertTrue(take(listener.failures).contains("Serial Number")); assertFalse(listener.retryable); }
        finally { session.close(); }
    }
    @Test public void disabledCodeProtectionUsesDefaultForMqttDespiteEnteredCode() throws Exception {
        Listener listener = new Listener(); List<FakeMqtt> clients = new CopyOnWriteArrayList<>();
        Cc2Session session = new Cc2Session("192.168.1.84", "stale-code", listener,
            url -> { fail("HTTP must not open"); return null; }, null,
            (uri, id) -> { FakeMqtt fake = new FakeMqtt(uri, id); clients.add(fake); return fake; },
            new PrinterIdentity("MANUAL", PrinterIdentityTest.reply(true, false)));
        try {
            session.connect(); take(listener.statuses); assertTrue(session.ready());
            assertEquals("123456", clients.get(0).password);
            assertTrue(clients.get(0).subscriptions.get(0).startsWith("elegoo/DISCOVERED/"));
            assertTrue(take(listener.results).contains("disabled"));
        } finally { session.close(); }
    }
    @Test public void reportedCloudModeDoesNotSendLanCredentialsToMqtt() throws Exception {
        Listener listener = new Listener();
        Cc2Session session = new Cc2Session("192.168.1.84", "code", listener,
            url -> { fail("HTTP must not open"); return null; }, null,
            (uri, id) -> { fail("Cloud mode must stop before MQTT LAN authentication"); return null; },
            new PrinterIdentity("", PrinterIdentityTest.reply(false, true)));
        try { session.connect(); assertTrue(take(listener.failures).contains("cloud / WAN mode")); assertFalse(listener.retryable); assertFalse(session.ready()); }
        finally { session.close(); }
    }
    private static Cc2Session session(Listener listener,List<FakeMqtt> clients,int machine,int sub) {
        return new Cc2Session("192.168.1.50","code",listener,url -> { throw new IOException("HTTP must remain optional"); },null,
            (uri,id) -> { FakeMqtt f=new FakeMqtt(uri,id); f.machine=machine;f.sub=sub;clients.add(f);return f; },http -> "TEST");
    }
    @Test public void queryResultsKeepRequestContextAndFailuresLeaveMonitoringConnected() throws Exception {
        Listener l=new Listener();List<FakeMqtt> clients=new CopyOnWriteArrayList<>();Cc2Session s=session(l,clients,2,2075);
        try {
            s.connect();take(l.statuses);FakeMqtt f=clients.get(0);s.files("u-disk",50);JSONObject query=take(l.queries);
            assertEquals(Cc2Codec.FILES,query.getInt("method"));assertEquals(50,query.getJSONObject("params").getInt("offset"));assertEquals("u-disk",query.getJSONObject("params").getString("storage_media"));assertEquals("test.gcode",query.getJSONObject("result").getJSONArray("file_list").getJSONObject(0).getString("filename"));
            f.rejectCamera=true;s.camera();assertTrue(take(l.queryErrors).startsWith(Cc2Codec.CAMERA+":"));assertTrue(s.ready());
            f.failDisk=true;s.disk();assertTrue(take(l.queryErrors).contains("could not be sent"));assertTrue(s.ready());
            f.failDisk=false;s.disk();assertEquals(1000,take(l.queries).getJSONObject("result").getInt("total_bytes"));assertTrue(s.fresh());
        } finally { s.close(); }
    }
    @Test public void resumePublishesOnlyWhenPausedAndUsesMatchingAcknowledgement() throws Exception {
        Listener l=new Listener();List<FakeMqtt> clients=new CopyOnWriteArrayList<>();Cc2Session s=session(l,clients,2,2502);
        try { s.connect();take(l.statuses);s.command(Cc2Codec.RESUME);FakeMqtt f=clients.get(0);JSONObject request=take(f.writes);assertEquals(1023,request.getInt("method"));assertEquals(0,request.getJSONObject("params").length());take(l.results);f.sub=2075;f.response(request.getInt("id"),1023,new JSONObject().put("error_code",0));assertTrue(take(l.results).contains("resume"));take(l.statuses);s.command(Cc2Codec.RESUME);assertTrue(take(l.results).contains("unavailable"));assertTrue(f.writes.isEmpty()); }
        finally {s.close();}
    }
    @Test public void queuedStartRechecksStateAtPublication() throws Exception {
        Listener l=new Listener();List<FakeMqtt> clients=new CopyOnWriteArrayList<>();Cc2Session s=session(l,clients,1,0);
        try {s.connect();take(l.statuses);FakeMqtt f=clients.get(0);s.start("local","test.gcode",true,false,false,"A",new org.json.JSONArray());
            JSONObject push=new JSONObject().put("method",6000).put("id",1).put("result",new JSONObject().put("machine_status",new JSONObject().put("status",2).put("sub_status",2075)));
            f.callback.messageArrived(f.subscriptions.get(1),new MqttMessage(push.toString().getBytes(StandardCharsets.UTF_8)));take(l.statuses);
            assertTrue(take(l.results).contains("Control unavailable"));assertTrue(f.writes.isEmpty());assertEquals(0,f.requests.stream().filter(r -> r.optInt("method")==Cc2Codec.START).count());
        } finally {s.close();}
    }
    @Test public void stopTakesNextSlotAheadOfQueuedFeatureReads() throws Exception {
        Listener l=new Listener();List<FakeMqtt> clients=new CopyOnWriteArrayList<>();Cc2Session s=session(l,clients,2,2075);
        try {s.connect();take(l.statuses);FakeMqtt f=clients.get(0);s.files("local",0);s.history();s.camera();s.command(Cc2Codec.STOP);JSONObject stop=take(f.writes);assertEquals(Cc2Codec.STOP,stop.getInt("method"));
            int position=-1;for(int i=0;i<f.requests.size();i++)if(f.requests.get(i).optInt("method")==Cc2Codec.STOP)position=i;
            assertTrue(position>=0);for(int i=0;i<position;i++)assertFalse(Cc2Codec.isQuery(f.requests.get(i).optInt("method")));
        }finally{s.close();}
    }

    @Test public void cloudPinProbeUsesOnlyExplicitPinAndRegistersWithoutHttp() throws Exception {
        Listener l = new Listener(); List<FakeMqtt> clients = new CopyOnWriteArrayList<>();
        Cc2Session s = new Cc2Session("192.168.1.84", "UNRELATED-LAN-CODE", l,
            url -> { fail("PIN must not open HTTP"); return null; }, null,
            (uri,id) -> { FakeMqtt f=new FakeMqtt(uri,id);clients.add(f);return f; },
            new PrinterIdentity("MANUAL", PrinterIdentityTest.reply(false,false), new PrinterAuthentication(true,"CURRENT-PIN")));
        try { s.connect();take(l.statuses);assertTrue(s.ready());assertTrue(s.readOnly());assertEquals("CURRENT-PIN",clients.get(0).password);assertEquals(1,clients.size());assertTrue(clients.get(0).subscriptions.get(0).startsWith("elegoo/DISCOVERED/"));assertFalse(take(l.results).contains("CURRENT-PIN")); }
        finally {s.close();}
    }
    @Test public void rejectedPinStopsWithoutLanFallbackOrAutomaticRetry() throws Exception {
        Listener l = new Listener(); java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        Cc2Session s = new Cc2Session("192.168.1.84", "UNRELATED-LAN-CODE", l,
            url -> { fail("HTTP must not open"); return null; }, null,
            (uri,id) -> new FakeMqtt(uri,id) { public void connect(MqttConnectOptions options) throws MqttException { attempts.incrementAndGet();assertEquals("CURRENT-PIN",new String(options.getPassword()));throw new MqttException(5); } },
            new PrinterIdentity("MANUAL",PrinterIdentityTest.reply(false,true),new PrinterAuthentication(true,"CURRENT-PIN")));
        try {s.connect();String failure=take(l.failures);assertTrue(failure.contains("PIN was not authorized"));assertTrue(failure.contains("Keep Matrix"));assertFalse(failure.contains("CURRENT-PIN"));assertFalse(failure.contains("UNRELATED-LAN-CODE"));assertFalse(l.retryable);assertEquals(1,attempts.get());assertFalse(s.ready());}
        finally {s.close();}
    }
    @Test public void pinProbeBlocksAllChangingMethodsAndUploadAtSessionBoundary() throws Exception {
        Listener l=new Listener();List<FakeMqtt> clients=new CopyOnWriteArrayList<>();
        Cc2Session s=new Cc2Session("192.168.1.84","IGNORED",l,url -> {fail("No PIN HTTP transport");return null;},null,
            (uri,id) -> {FakeMqtt f=new FakeMqtt(uri,id);clients.add(f);return f;},
            new PrinterIdentity("MANUAL",PrinterIdentityTest.reply(false,true),new PrinterAuthentication(true,"CURRENT-PIN")));
        File upload=File.createTempFile("pin-probe-", ".gcode");
        try {s.connect();take(l.statuses);take(l.canvases);s.command(Cc2Codec.PAUSE);s.command(Cc2Codec.RESUME);s.command(Cc2Codec.STOP);
            s.start("local","test.gcode",true,false,false,"A",new org.json.JSONArray());s.delete("local","test.gcode");s.light(true);s.temperatures(100,40);s.fan("fan",50);s.speed(0);s.autoRefill(true);s.upload(upload,"test.gcode");assertFalse(s.download(upload,"local","test.gcode"));assertFalse(s.downloading());
            s.files("local",0);assertEquals(Cc2Codec.FILES,take(l.queries).getInt("method"));
            assertTrue(clients.get(0).writes.isEmpty());assertTrue(clients.get(0).requests.stream().noneMatch(r -> Cc2Codec.changing(r.optInt("method"))));assertFalse(s.uploading());assertTrue(s.ready());
            assertTrue(l.results.stream().anyMatch(r -> r.contains("uploads are disabled")));assertTrue(l.results.stream().anyMatch(r -> r.contains("printer-changing commands are disabled")));
        }finally{s.close();upload.delete();}
    }
    @Test public void pinProbeConnectionLossIsTerminal() throws Exception {
        Listener l=new Listener();List<FakeMqtt> clients=new CopyOnWriteArrayList<>();
        Cc2Session s=new Cc2Session("192.168.1.84","",l,url -> {fail("HTTP must not open");return null;},null,
            (uri,id) -> {FakeMqtt f=new FakeMqtt(uri,id);clients.add(f);return f;},
            new PrinterIdentity("MANUAL",PrinterIdentityTest.reply(false,true),new PrinterAuthentication(true,"CURRENT-PIN")));
        try{s.connect();take(l.statuses);clients.get(0).callback.connectionLost(new IOException("gone"));take(l.failures);assertFalse(l.retryable);assertFalse(s.ready());assertEquals(1,clients.size());}
        finally{s.close();}
    }
    @Test public void pinProbeRegistrationLimitDoesNotSuggestClosingMatrixOrExposeRawReply() throws Exception {
        Listener l=new Listener();
        Cc2Session s=new Cc2Session("192.168.1.84","",l,url -> {fail("HTTP must not open");return null;},null,
            (uri,id) -> new FakeMqtt(uri,id) { public void publish(String topic,byte[] bytes,int qos,boolean retained) throws MqttException {
                if (!topic.endsWith("api_register")) {super.publish(topic,bytes,qos,retained);return;}
                try {JSONObject request=new JSONObject(new String(bytes,StandardCharsets.UTF_8));callback.messageArrived(subscriptions.get(2),new MqttMessage(new JSONObject().put("client_id",request.getString("client_id")).put("error","too many clients: secret").toString().getBytes(StandardCharsets.UTF_8)));}
                catch(Exception error){throw new MqttException(error);}
            } },new PrinterIdentity("MANUAL",PrinterIdentityTest.reply(false,true),new PrinterAuthentication(true,"CURRENT-PIN")));
        try{s.connect();String failure=take(l.failures);assertTrue(failure.contains("connection limit"));assertTrue(failure.contains("this app did not request other clients to disconnect"));assertFalse(failure.contains("secret"));assertFalse(failure.contains("close other"));assertFalse(l.retryable);}
        finally{s.close();}
    }

    @Test public void pinRegistrationTimeoutReportsBrokerStagesWithoutClaimingHttpOrPinValidity() throws Exception {
        Listener l = new Listener(); java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        Cc2Session s = new Cc2Session("192.168.1.84", "", l, url -> { fail("No HTTP in PIN probe"); return null; }, null,
            (uri,id) -> { attempts.incrementAndGet(); return new FakeMqtt(uri,id) {
                @Override public void publish(String topic, byte[] bytes, int qos, boolean retained) throws MqttException {
                    assertTrue(topic.endsWith("api_register")); assertEquals(1,qos); assertFalse(retained);
                    try {
                        JSONObject req = new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                        callback.messageArrived(subscriptions.get(2), new MqttMessage("PRIVATE-PIN".getBytes(StandardCharsets.UTF_8)));
                        callback.messageArrived(subscriptions.get(2), new MqttMessage(new JSONObject().put("client_id","wrong-client").put("error","PRIVATE-PIN").toString().getBytes(StandardCharsets.UTF_8)));
                        MqttMessage old = new MqttMessage(new JSONObject().put("client_id",req.getString("client_id")).put("error","ok").toString().getBytes(StandardCharsets.UTF_8)); old.setRetained(true);
                        callback.messageArrived(subscriptions.get(2), old);
                        callback.messageArrived(subscriptions.get(1), new MqttMessage("{}".getBytes(StandardCharsets.UTF_8)));
                        callback.messageArrived(subscriptions.get(0), new MqttMessage("{}".getBytes(StandardCharsets.UTF_8)));
                        callback.messageArrived("elegoo/OTHER-SERIAL/"+req.getString("request_id")+"/register_response", new MqttMessage("{}".getBytes(StandardCharsets.UTF_8)));
                    } catch (Exception e) { throw new MqttException(e); }
                }
            }; }, new PrinterIdentity("",PrinterIdentityTest.reply(false,true),new PrinterAuthentication(true,"PRIVATE-PIN")));
        try {
            s.connect(); String text = take(l.failures);
            assertTrue(text.contains("timed out")); assertFalse(text.contains("HTTP")); assertFalse(text.contains("PRIVATE-PIN"));
            assertFalse(text.contains("wrong-client")); assertFalse(text.contains("OTHER-SERIAL"));
            assertTrue(text.contains("3/3 accepted (SUBACK)")); assertTrue(text.contains("broker acknowledged (QoS 1 / PUBACK)"));
            assertTrue(text.contains("Registration reply: none")); assertTrue(text.contains("malformed 1, wrong client 1, retained 1"));
            assertTrue(text.contains("status 1, API replies 1, unexpected topic 1"));
            assertFalse(s.ready()); assertFalse(l.retryable); assertEquals(1,attempts.get()); assertTrue(l.statuses.isEmpty());
        } finally { s.close(); }
    }
    @Test public void deniedSubscriptionStopsBeforePublishingRegistration() throws Exception {
        Listener l = new Listener(); java.util.concurrent.atomic.AtomicInteger publishes = new java.util.concurrent.atomic.AtomicInteger();
        Cc2Session s = new Cc2Session("192.168.1.84", "", l, url -> { fail("No HTTP"); return null; }, null,
            (uri,id) -> new FakeMqtt(uri,id) {
                @Override public IMqttToken subscribeWithResponse(String[] topics,int[] qos) {
                    return new MqttToken() { @Override public int[] getGrantedQos() { return new int[] {1,1,128}; } };
                }
                @Override public void publish(String topic,byte[] bytes,int qos,boolean retained) { publishes.incrementAndGet(); }
            }, new PrinterIdentity("",PrinterIdentityTest.reply(false,true),new PrinterAuthentication(true,"PRIVATE-PIN")));
        try {
            s.connect(); String text=take(l.failures); assertTrue(text.contains("rejected: registration replies"));
            assertTrue(text.contains("Registration publish: not attempted")); assertEquals(0,publishes.get()); assertFalse(s.ready()); assertFalse(l.retryable);
        } finally { s.close(); }
    }
    @Test public void publishFailureDoesNotClaimBrokerAcknowledgedRegistration() throws Exception {
        Listener l = new Listener();
        Cc2Session s = new Cc2Session("192.168.1.84", "", l, url -> { fail("No HTTP"); return null; }, null,
            (uri,id) -> new FakeMqtt(uri,id) {
                @Override public void publish(String topic,byte[] bytes,int qos,boolean retained) throws MqttException { throw new MqttException(new IOException("PRIVATE-PIN")); }
            }, new PrinterIdentity("",PrinterIdentityTest.reply(false,true),new PrinterAuthentication(true,"PRIVATE-PIN")));
        try {
            s.connect(); String text=take(l.failures); assertTrue(text.contains("PUBACK not completed"));
            assertFalse(text.contains("broker acknowledged")); assertFalse(text.contains("PRIVATE-PIN")); assertFalse(s.ready()); assertFalse(l.retryable);
        } finally { s.close(); }
    }
    @Test public void wrongClientReplyCannotPreventFollowingValidRegistration() throws Exception {
        Listener l = new Listener();
        Cc2Session s = new Cc2Session("192.168.1.84", "", l, url -> { fail("No HTTP"); return null; }, null,
            (uri,id) -> new FakeMqtt(uri,id) {
                @Override public void publish(String topic,byte[] bytes,int qos,boolean retained) throws MqttException {
                    if (topic.endsWith("api_register")) try {
                        callback.messageArrived(subscriptions.get(2),new MqttMessage("{\"client_id\":\"wrong\",\"error\":\"ok\"}".getBytes(StandardCharsets.UTF_8)));
                    } catch (Exception e) { throw new MqttException(e); }
                    super.publish(topic,bytes,qos,retained);
                }
            }, new PrinterIdentity("",PrinterIdentityTest.reply(false,true),new PrinterAuthentication(true,"PRIVATE-PIN")));
        try { s.connect(); take(l.statuses); assertTrue(s.ready()); assertTrue(l.failures.isEmpty()); }
        finally { s.close(); }
    }

    @Test public void downloadUsesEffectiveSessionTokenAndDoesNotPublishChangingRequest() throws Exception {
        Listener l = new Listener(); List<FakeMqtt> clients = new CopyOnWriteArrayList<>(); List<PrinterDownloadTest.Connection> connections = new CopyOnWriteArrayList<>();
        Cc2Session s = new Cc2Session("192.168.1.84", "OLD-TYPED-CODE", l, url -> { PrinterDownloadTest.Connection c = new PrinterDownloadTest.Connection(url); connections.add(c); return c; }, null,
            (uri,id) -> { FakeMqtt f = new FakeMqtt(uri,id); clients.add(f); return f; }, new PrinterIdentity("MANUAL", PrinterIdentityTest.reply(true,false)));
        File file = File.createTempFile("session-download-", ".gcode");
        try {
            s.connect(); take(l.statuses); assertEquals("123456", clients.get(0).password); assertTrue(s.download(file,"u-disk","two words.gcode"));
            assertEquals(file, take(l.downloads)); assertArrayEquals(connections.get(0).body, java.nio.file.Files.readAllBytes(file.toPath()));
            assertEquals("123456", connections.get(0).getRequestProperty("X-Token")); assertEquals("X-Token=123456&file_name=two+words.gcode",connections.get(0).getURL().getQuery());
            assertTrue(clients.get(0).writes.isEmpty()); assertTrue(s.ready());
        } finally { s.close(); file.delete(); }
    }
    @Test public void closingSessionCancelsDownloadAndRemovesPartialFileWithoutReplay() throws Exception {
        Listener l = new Listener(); CountDownLatch reading = new CountDownLatch(1), released = new CountDownLatch(1); List<PrinterDownloadTest.Connection> connections = new CopyOnWriteArrayList<>();
        Cc2Session s = new Cc2Session("192.168.1.84", "code", l, url -> {
            PrinterDownloadTest.Connection c = new PrinterDownloadTest.Connection(url) {
                public void disconnect() { super.disconnect(); released.countDown(); }
                public InputStream getInputStream() { return new InputStream() {
                    boolean first = true;
                    public int read() throws IOException { throw new IOException("Use buffered read"); }
                    public int read(byte[] bytes,int off,int len) throws IOException {
                        if (first) { first = false; bytes[off] = 'G'; reading.countDown(); return 1; }
                        try { if (!released.await(5,TimeUnit.SECONDS)) throw new IOException("Timed out"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        throw new IOException("Connection closed");
                    }
                }; }
            }; c.length = 100; connections.add(c); return c;
        }, null, (uri,id) -> new FakeMqtt(uri,id), http -> "SERIAL");
        File file = File.createTempFile("session-download-", ".gcode");
        try {
            s.connect(); take(l.statuses); assertTrue(s.download(file,"local","test.gcode")); assertTrue(reading.await(3,TimeUnit.SECONDS)); assertTrue(s.downloading());
            File other = File.createTempFile("rejected-", ".gcode"); try { assertFalse(s.download(other,"local","test.gcode")); } finally { other.delete(); }
            s.close(); assertTrue(released.await(2,TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3); while(file.exists() && System.nanoTime() < deadline) Thread.sleep(10);
            assertFalse(file.exists()); assertTrue(connections.get(0).disconnected); assertEquals(1, connections.size()); assertTrue(l.downloads.isEmpty());
        } finally { s.close(); file.delete(); }
    }
}
