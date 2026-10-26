package io.github.thelastfrogrammer.elink;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrintAlertsTest {
    private static JSONObject state(int machine,int sub,String job) throws Exception { return new JSONObject().put("machine_status",new JSONObject().put("status",machine).put("sub_status",sub)).put("print_status",new JSONObject().put("uuid",job).put("filename",job.isEmpty()?"":job+".gcode")); }
    @Test public void completionRequiresObservedJobAndOnlyAlertsOnce() throws Exception {
        PrintAlerts alerts=new PrintAlerts(); assertEquals("",alerts.update(state(2,2077,"a"))); assertEquals("",alerts.update(state(2,2075,"a")));
        assertEquals("Print complete: a.gcode",alerts.update(state(2,2077,"a"))); assertEquals("",alerts.update(state(2,2077,"a")));
    }
    @Test public void idleCancellationAndDisconnectedSessionsDoNotImplyCompletion() throws Exception {
        for (int end : new int[] {2503,2504,-1}) { PrintAlerts a=new PrintAlerts(); a.update(state(2,2075,"a")); a.update(state(end==-1?1:2,end,"a")); assertEquals("",a.update(state(2,2077,"a"))); }
        PrintAlerts a=new PrintAlerts(); a.update(state(2,2075,"a")); a.disconnected(); assertEquals("",a.update(state(2,2077,"a")));
    }
    @Test public void clearedCompletionFilenameUsesObservedNameButOtherJobIsRejected() throws Exception {
        PrintAlerts a=new PrintAlerts(); a.update(state(2,2075,"a")); assertEquals("Print complete: a.gcode",a.update(state(2,2077,"")));
        a.update(state(2,2075,"a")); assertEquals("",a.update(state(2,2077,"b")));
    }
    @Test public void unchangedFaultDoesNotRepeatButNewOrReappearingFaultDoes() throws Exception {
        PrintAlerts a=new PrintAlerts(); JSONObject s=state(1,0,""); s.put("exception",new JSONObject("{\"exception_code\":{\"803\":{}}}"));
        assertEquals("Printer fault: 803",a.update(s)); assertEquals("",a.update(s)); a.disconnected(); assertEquals("",a.update(s));
        s.getJSONObject("exception").getJSONObject("exception_code").put("1101",new JSONObject()); assertTrue(a.update(s).contains("1101"));
        s.getJSONObject("exception").put("exception_code",new JSONObject()); assertEquals("",a.update(s)); s.getJSONObject("exception").put("exception_code",new JSONObject("{\"803\":{}}")); assertEquals("Printer fault: 803",a.update(s));
    }
}
