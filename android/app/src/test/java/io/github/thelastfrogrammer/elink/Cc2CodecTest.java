package io.github.thelastfrogrammer.elink;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class Cc2CodecTest {
    @Test public void deltaPreservesHeaterTargetButClearsOldExceptions() throws Exception {
        Cc2Codec codec = new Cc2Codec();
        assertTrue(codec.accept(new JSONObject("{\"method\":1002,\"result\":{\"error_code\":0,\"extruder\":{\"temperature\":201,\"target\":220},\"exception_code\":{\"old\":42},\"fans\":[1,2]}}")));
        assertTrue(codec.accept(new JSONObject("{\"method\":6000,\"id\":1,\"result\":{\"extruder\":{\"temperature\":210},\"exception_code\":{},\"fans\":[3]}}")));
        JSONObject status = codec.snapshot();
        assertEquals(220, status.getJSONObject("extruder").getInt("target"));
        assertEquals(210, status.getJSONObject("extruder").getInt("temperature"));
        assertEquals(0, status.getJSONObject("exception_code").length());
        assertEquals(1, status.getJSONArray("fans").length());
    }
    @Test public void deltaCannotInventBaselineAndRepeatedGapsInvalidateIt() throws Exception {
        Cc2Codec codec = new Cc2Codec();
        assertFalse(codec.accept(new JSONObject("{\"method\":6000,\"id\":1,\"result\":{}}")));
        assertTrue(codec.accept(new JSONObject("{\"method\":1002,\"result\":{\"error_code\":0}}")));
        for (int id : new int[] {1, 3, 5, 7, 9}) assertTrue(codec.accept(new JSONObject().put("method", 6000).put("id", id).put("result", new JSONObject())));
        assertFalse(codec.accept(new JSONObject().put("method", 6000).put("id", 11).put("result", new JSONObject())));
        assertEquals(0, codec.snapshot().length());
    }
    @Test public void zeroSequenceRestartsAndFullSnapshotReplacesOldData() throws Exception {
        Cc2Codec codec = new Cc2Codec();
        codec.accept(new JSONObject("{\"method\":1002,\"result\":{\"error_code\":0,\"old\":1}}"));
        assertTrue(codec.accept(new JSONObject("{\"method\":6000,\"id\":0,\"result\":{}}")));
        codec.accept(new JSONObject("{\"method\":1002,\"result\":{\"error_code\":0}}"));
        assertFalse(codec.snapshot().has("old"));
    }
    @Test public void registrationRequiresBothClientIdentityAndSuccess() throws Exception {
        assertFalse(Cc2Codec.validRegistration(new JSONObject("{\"client_id\":\"other\",\"error\":\"ok\"}"), "ours"));
        assertFalse(Cc2Codec.validRegistration(new JSONObject("{\"client_id\":\"ours\",\"error\":\"too many clients\"}"), "ours"));
        assertTrue(Cc2Codec.validRegistration(new JSONObject("{\"client_id\":\"ours\",\"error\":\"ok\"}"), "ours"));
    }
    @Test public void axisMovementAndUnknownCommandsAreNotSent() throws Exception {
        assertEquals(1023, Cc2Codec.request(1, Cc2Codec.RESUME).getInt("method"));
        try { Cc2Codec.request(1, 1027); fail("Unverified axis movement remains unsupported"); } catch (IllegalArgumentException expected) { }
        assertEquals(1021, Cc2Codec.request(77, Cc2Codec.PAUSE).getInt("method"));
        assertEquals(77, Cc2Codec.request(77, Cc2Codec.PAUSE).getInt("id"));
        assertEquals(0, Cc2Codec.request(77, Cc2Codec.PAUSE).getJSONObject("params").length());
    }
    @Test public void pausedAndIdleStatesDisablePauseAndStopIsConstrained() throws Exception {
        JSONObject paused = new JSONObject("{\"machine_status\":{\"status\":2,\"sub_status\":2502}}"), idle = new JSONObject("{\"machine_status\":{\"status\":1}}"), printing = new JSONObject("{\"machine_status\":{\"status\":2,\"sub_status\":2075}}"), stopped = new JSONObject("{\"machine_status\":{\"status\":2,\"sub_status\":2504}}" );
        assertFalse(Cc2Codec.canPause(paused)); assertTrue(Cc2Codec.canStop(paused));
        assertFalse(Cc2Codec.canStop(idle)); assertTrue(Cc2Codec.canPause(printing));
        assertFalse(Cc2Codec.canStop(stopped));
    }
    @Test public void errorSnapshotCannotReplaceWorkingStatus() throws Exception {
        Cc2Codec codec = new Cc2Codec();
        codec.accept(new JSONObject("{\"method\":1002,\"result\":{\"error_code\":0,\"marker\":1}}"));
        assertFalse(codec.accept(new JSONObject("{\"method\":1002,\"result\":{\"error_code\":1000}}")));
        assertEquals(1, codec.snapshot().getInt("marker"));
    }
    @Test public void callerCannotMutateCachedStatus() throws Exception {
        Cc2Codec codec = new Cc2Codec();
        codec.accept(new JSONObject("{\"method\":1002,\"result\":{\"error_code\":0,\"marker\":1}}"));
        codec.snapshot().put("marker", 2);
        assertEquals(1, codec.snapshot().getInt("marker"));
    }
    @Test public void onlyLocalAddressesAcceptCredentials() {
        for (String host : new String[] {"https://192.168.1.2/", "8.8.8.8", "192.168.1.999", "127.0.0.1", "example.com", "192.168.1.1:80"}) {
            try { new PrinterHttp(host, "secret"); fail(host); } catch (IllegalArgumentException expected) { }
        }
        assertEquals("192.168.1.2", new PrinterHttp("192.168.1.2", "secret").host());
    }
}
