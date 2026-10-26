package io.github.thelastfrogrammer.elink;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeatureProtocolTest {
    private interface Action { void run() throws Exception; }
    private static void rejected(Action action) throws Exception { try { action.run(); fail("Unsafe input accepted"); } catch (IllegalArgumentException expected) { } }
    @Test public void printSetupUsesSdkFieldsAndCopiesMappings() throws Exception {
        JSONArray maps = new JSONArray("[{\"t\":0,\"canvas_id\":2,\"tray_id\":3}]");
        JSONObject request = Cc2Codec.startRequest(77, "local", "Colour benchy.gcode", true, true, true, "B", maps);
        assertEquals(1020, request.getInt("method")); assertEquals(77, request.getInt("id"));
        JSONObject params = request.getJSONObject("params"), config = params.getJSONObject("config");
        assertEquals("Colour benchy.gcode", params.getString("filename")); assertEquals("local", params.getString("storage_media"));
        assertEquals(5, config.length()); assertTrue(config.getBoolean("printer_check")); assertTrue(config.getBoolean("bedlevel_force")); assertTrue(config.getBoolean("delay_video")); assertEquals("B", config.getString("print_layout"));
        maps.getJSONObject(0).put("tray_id", 8); assertEquals(3, config.getJSONArray("slot_map").getJSONObject(0).getInt("tray_id"));
    }
    @Test public void dangerousPrintInputsAreRejected() throws Exception {
        rejected(() -> Cc2Codec.startRequest(1, "local", "../test.gcode", true, false, false, "A", new JSONArray()));
        rejected(() -> Cc2Codec.startRequest(1, "cloud", "test.gcode", true, false, false, "A", new JSONArray()));
        rejected(() -> Cc2Codec.startRequest(1, "local", "test.gcode", false, true, false, "A", new JSONArray()));
        rejected(() -> Cc2Codec.startRequest(1, "local", "test.gcode", true, false, false, "C", new JSONArray()));
        rejected(() -> Cc2Codec.startRequest(1, "local", "test.gcode", true, false, false, "A", new JSONArray("[{\"t\":0,\"canvas_id\":1,\"tray_id\":2},{\"t\":0,\"canvas_id\":1,\"tray_id\":3}]")));
        rejected(() -> Cc2Codec.startRequest(1, "local", "test.gcode", true, false, false, "A", new JSONArray("[{\"t\":8,\"canvas_id\":1,\"tray_id\":2}]")));
    }
    @Test public void fileQueriesUseOffsetAndUsbRootAndDeleteUsesAnArray() throws Exception {
        JSONObject local = Cc2Codec.filesRequest(1,"local",50).getJSONObject("params"), usb = Cc2Codec.filesRequest(2,"u-disk",0).getJSONObject("params");
        assertEquals(50, local.getInt("offset")); assertEquals(50,local.getInt("limit")); assertFalse(local.has("dir")); assertEquals("/",usb.getString("dir"));
        JSONObject deletion = Cc2Codec.deleteRequest(3,"u-disk","模型.gcode").getJSONObject("params"); assertEquals("模型.gcode",deletion.getJSONArray("file_path").getString(0));
        rejected(() -> Cc2Codec.deleteRequest(1,"local","bad\n\nname.gcode")); rejected(() -> Cc2Codec.filesRequest(1,"local",-1)); rejected(() -> Cc2Codec.deleteRequest(1,"local","job.gcode\n"));
    }
    @Test public void heaterFanSpeedAndLightLimitsMatchTheirWireFormats() throws Exception {
        JSONObject heaters = Cc2Codec.temperatureRequest(1,300,100).getJSONObject("params"); assertEquals(300,heaters.getInt("extruder")); assertEquals(100,heaters.getInt("heater_bed"));
        rejected(() -> Cc2Codec.temperatureRequest(1,301,100)); rejected(() -> Cc2Codec.temperatureRequest(1,200,-1));
        assertEquals(0,Cc2Codec.fanRequest(1,"fan",0).getJSONObject("params").getInt("fan"));
        assertEquals(128,Cc2Codec.fanRequest(1,"aux_fan",50).getJSONObject("params").getInt("aux_fan"));
        assertEquals(255,Cc2Codec.fanRequest(1,"box_fan",100).getJSONObject("params").getInt("box_fan"));
        rejected(() -> Cc2Codec.fanRequest(1,"guess",50)); rejected(() -> Cc2Codec.fanRequest(1,"fan",101));
        assertEquals(3,Cc2Codec.speedRequest(1,3).getJSONObject("params").getInt("mode")); rejected(() -> Cc2Codec.speedRequest(1,4));
        assertEquals(1,Cc2Codec.lightRequest(1,true).getJSONObject("params").getInt("power")); assertEquals(0,Cc2Codec.lightRequest(1,false).getJSONObject("params").getInt("power"));
    }
    @Test public void resumeRequiresExplicitPausedState() throws Exception {
        for (int sub : new int[] {2502,2505}) assertTrue(Cc2Codec.canResume(new JSONObject("{\"machine_status\":{\"status\":2,\"sub_status\":"+sub+"}}")));
        for (int sub : new int[] {2075,2077,2501,2503,2504,9999}) assertFalse(Cc2Codec.canResume(new JSONObject("{\"machine_status\":{\"status\":2,\"sub_status\":"+sub+"}}")));
        assertFalse(Cc2Codec.canResume(new JSONObject("{\"machine_status\":{\"status\":1,\"sub_status\":2502}}")));
    }
    @Test public void fileAndCameraResultsRequireTheExpectedShape() throws Exception {
        assertTrue(Cc2Codec.queryShape(Cc2Codec.FILES,new JSONObject("{\"file_list\":[]}"))); assertFalse(Cc2Codec.queryShape(Cc2Codec.FILES,new JSONObject("{\"error_code\":0}")));
        assertFalse(Cc2Codec.queryShape(Cc2Codec.DISK,new JSONObject("{\"used_bytes\":\"100\",\"total_bytes\":200}")));
        assertTrue(Cc2Codec.queryShape(Cc2Codec.CAMERA,new JSONObject("{\"url\":\"http://192.168.1.50:8080/\"}")));
    }
    @Test public void mappingRequiresConnectedReportedTrayWithMaterial() throws Exception {
        JSONArray maps = new JSONArray("[{\"t\":0,\"canvas_id\":5,\"tray_id\":3}]");
        JSONObject canvas = new JSONObject("{\"canvas_list\":[{\"canvas_id\":5,\"connected\":1,\"tray_list\":[{\"tray_id\":3,\"filament_type\":\"PLA\"}]}]}");
        assertTrue(FeatureData.mappings(canvas,maps)); canvas.getJSONArray("canvas_list").getJSONObject(0).put("connected",false); assertFalse(FeatureData.mappings(canvas,maps));
        canvas.getJSONArray("canvas_list").getJSONObject(0).put("connected",true).getJSONArray("tray_list").getJSONObject(0).put("filament_type",""); assertFalse(FeatureData.mappings(canvas,maps));
        assertTrue(FeatureData.mappings(null,new JSONArray())); assertFalse(FeatureData.mappings(null,maps));
    }
    @Test public void cameraCannotSendRequestsToOtherHostsOrEmbeddedAccounts() throws Exception {
        assertEquals("http://192.168.1.50:8080/?action=stream",FeatureData.cameraUrl("192.168.1.50","http://192.168.1.50:8080/?action=stream"));
        for (String url : new String[] {"http://192.168.1.51:8080/", "http://user:secret@192.168.1.50:8080/", "file:///etc/passwd", "http://192.168.1.50:0/", "http://192.168.1.50:65536/", "http://192.168.1.50/#token"}) rejected(() -> FeatureData.cameraUrl("192.168.1.50",url));
    }
    @Test public void historyUsesNewestFirstAndLeavesUnknownStateExplicit() throws Exception {
        String text=FeatureData.history(new JSONObject("{\"history_task_list\":[{\"task_name\":\"old\",\"task_status\":1},{\"task_name\":\"new\",\"task_status\":99}]}"));
        assertTrue(text.indexOf("new")<text.indexOf("old")); assertTrue(text.contains("Reported state 99")); assertTrue(text.contains("Completed"));
    }
}
