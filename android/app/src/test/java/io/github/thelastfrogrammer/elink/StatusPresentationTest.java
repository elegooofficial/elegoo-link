package io.github.thelastfrogrammer.elink;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class StatusPresentationTest {
    @Test public void canvasQueryUpdatesBaselineWithoutBeingUndoneByUnrelatedDelta() throws Exception {
        Cc2Codec codec = new Cc2Codec();
        codec.accept(new JSONObject("{\"method\":1002,\"result\":{\"error_code\":0,\"canvas_info\":{\"auto_refill\":false}}}"));
        codec.canvas(new JSONObject("{\"auto_refill\":true,\"active_tray_id\":2}"));
        codec.accept(new JSONObject("{\"method\":6000,\"id\":1,\"result\":{\"extruder\":{\"temperature\":210}}}"));
        assertTrue(codec.snapshot().getJSONObject("canvas_info").getBoolean("auto_refill"));
        codec.accept(new JSONObject("{\"method\":6000,\"id\":2,\"result\":{\"canvas_info\":{\"active_tray_id\":3}}}"));
        assertEquals(3, codec.snapshot().getJSONObject("canvas_info").getInt("active_tray_id"));
        assertTrue(codec.snapshot().getJSONObject("canvas_info").getBoolean("auto_refill"));
    }
    @Test public void faultsUseReportedCodesAndDisappearWhenCleared() throws Exception {
        Cc2Codec codec = new Cc2Codec();
        codec.accept(new JSONObject("{\"method\":1002,\"result\":{\"error_code\":0,\"exception\":{\"exception_code\":{\"1101\":{},\"803\":{}}}}}"));
        assertEquals("1101, 803", StatusPresentation.faultCodes(codec.snapshot()));
        codec.accept(new JSONObject("{\"method\":6000,\"id\":1,\"result\":{\"exception\":{\"exception_code\":{}}}}"));
        assertEquals("", StatusPresentation.faultCodes(codec.snapshot()));
    }
    @Test public void onlyAnExplicitMatchingTrayIsActiveAndUnknownStateIsNotInvented() throws Exception {
        JSONObject canvas = new JSONObject("{\"auto_refill\":true,\"active_canvas_id\":2,\"active_tray_id\":3,\"canvas_list\":[{\"canvas_id\":2,\"connected\":1,\"tray_list\":[{\"tray_id\":3,\"filament_type\":\"PLA\",\"filament_color\":\"#44aa33\",\"status\":99,\"min_nozzle_temp\":190,\"max_nozzle_temp\":230}]}]}");
        String text = StatusPresentation.canvas(canvas);
        assertTrue(text.contains("Automatic refill: On")); assertTrue(text.contains("Tray 3 · Active"));
        assertTrue(text.contains("#44aa33")); assertTrue(text.contains("State 99")); assertTrue(text.contains("190–230°C"));
        canvas.remove("active_tray_id"); assertFalse(StatusPresentation.canvas(canvas).contains("· Active"));
    }
    @Test public void missingCanvasDoesNotClaimDisconnectedOrRefillOff() {
        assertTrue(StatusPresentation.canvas(null).contains("not been reported"));
        assertTrue(StatusPresentation.canvas(new JSONObject()).contains("Not reported"));
    }
    @Test public void unsupportedSubstateStaysExplicitAndDoesNotEnablePause() throws Exception {
        JSONObject status = new JSONObject("{\"machine_status\":{\"status\":2,\"sub_status\":123456}}");
        assertTrue(StatusPresentation.state(status).contains("123456")); assertFalse(Cc2Codec.canPause(status));
    }
}
