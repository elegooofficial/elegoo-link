package io.github.thelastfrogrammer.elink;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class PrinterDownloadTest {
    static class Connection extends HttpURLConnection {
        byte[] body = "G28\nT0\n".getBytes(StandardCharsets.UTF_8);
        int code = 200; long length = body.length; String type = "application/octet-stream", encoding;
        boolean disconnected;
        Connection(URL url) { super(url); }
        public void disconnect() { disconnected = true; } public boolean usingProxy() { return false; } public void connect() { }
        public int getResponseCode() { return code; } public long getContentLengthLong() { return length; }
        public String getContentType() { return type; } public String getContentEncoding() { return encoding; }
        public InputStream getInputStream() { return new ByteArrayInputStream(body); }
    }
    @Test public void internalAndUsbUseEncodedSeparateTokenFilenameQueryAndNoRedirects() throws Exception {
        for (String storage : Arrays.asList("local", "u-disk")) {
            List<Connection> opened = new ArrayList<>(); List<Integer> progress = new ArrayList<>(); File file = File.createTempFile("download-", ".gcode");
            try {
                new PrinterHttp("192.168.1.50", "a&? token", url -> { Connection c = new Connection(url); opened.add(c); return c; }).download(file, storage, "two words.gcode", progress::add);
                Connection c = opened.get(0); assertEquals(storage.equals("local") ? "/download" : "/download/udisk", c.getURL().getPath());
                assertEquals("X-Token=a%26%3F+token&file_name=two+words.gcode", c.getURL().getQuery()); assertEquals("a&? token", c.getRequestProperty("X-Token"));
                assertEquals("GET", c.getRequestMethod()); assertFalse(c.getInstanceFollowRedirects()); assertTrue(c.disconnected);
                assertArrayEquals(c.body, Files.readAllBytes(file.toPath())); assertEquals(Integer.valueOf(100), progress.get(progress.size() - 1));
            } finally { file.delete(); }
        }
    }
    private void rejected(int status, long length, String type, String encoding, byte[] body) throws Exception {
        File file = File.createTempFile("download-", ".gcode"); List<Connection> opened = new ArrayList<>();
        try {
            PrinterHttp http = new PrinterHttp("192.168.1.50", "code", url -> { Connection c = new Connection(url); c.code = status; c.length = length; c.type = type; c.encoding = encoding; c.body = body; opened.add(c); return c; });
            try { http.download(file, "local", "test.gcode", p -> { }); fail("Must reject"); } catch (IOException expected) { }
            assertFalse("Failed download must remove partial cache copy", file.exists()); assertTrue(opened.get(0).disconnected);
        } finally { file.delete(); }
    }
    @Test public void authorizationRedirectAndErrorResponsesNeverBecomeFiles() throws Exception {
        for (int code : new int[] {401, 403, 302, 404, 500}) rejected(code, 2, null, null, new byte[] {1, 2});
        rejected(200, 2, "application/json", null, new byte[] {1, 2}); rejected(200, 2, "text/html", null, new byte[] {1, 2});
        rejected(200, 2, null, "gzip", new byte[] {1, 2});
        rejected(200, -1, null, null, " \n{\"error_code\":1000}".getBytes(StandardCharsets.UTF_8));
    }
    @Test public void oversizedEmptyAndTruncatedPayloadsDeleteCacheCopy() throws Exception {
        rejected(200, GcodeInspector.MAX_BYTES + 1, null, null, new byte[] {1});
        rejected(200, 0, null, null, new byte[0]); rejected(200, -1, null, null, new byte[0]);
        rejected(200, 9, null, null, "G28".getBytes(StandardCharsets.UTF_8));
        rejected(200, 2, null, null, "G28".getBytes(StandardCharsets.UTF_8));
    }
    @Test public void unknownLengthStreamsWithoutInventingPercentageThenCompletes() throws Exception {
        File file = File.createTempFile("download-", ".gcode"); List<Integer> progress = new ArrayList<>();
        try { new PrinterHttp("192.168.1.50", "", url -> { Connection c = new Connection(url); c.length = -1; return c; }).download(file, "local", "test.gcode", progress::add); assertEquals(Arrays.asList(-1, 100), progress); }
        finally { file.delete(); }
    }
    @Test public void cancellationMidStreamRemovesPartialAndDoesNotReportCompletion() throws Exception {
        File file = File.createTempFile("download-", ".gcode"); List<Integer> progress = new ArrayList<>();
        PrinterHttp http = new PrinterHttp("192.168.1.50", "code", url -> { Connection c = new Connection(url); c.body = new byte[140000]; Arrays.fill(c.body, (byte) 'G'); c.length = c.body.length; return c; });
        try { try { http.download(file, "local", "test.gcode", p -> { progress.add(p); http.cancel(); }); fail(); } catch (IOException expected) { } assertFalse(file.exists()); assertFalse(progress.contains(100)); }
        finally { file.delete(); }
    }
    @Test public void cancelledBeforeOpeningAndUnsafePathCannotCreateARequest() throws Exception {
        PrinterHttp http = new PrinterHttp("192.168.1.50", "code", url -> { throw new AssertionError("Must not open"); }); http.cancel();
        File file = File.createTempFile("download-", ".gcode");
        try {
            try { http.download(file, "local", "test.gcode", p -> { }); fail(); } catch (IOException expected) { } assertFalse(file.exists());
            for (String filename : Arrays.asList("../test.gcode", "test.gcode&other=1", "test.gcode\nX:secret")) try { http.download(file, "local", filename, p -> { }); fail(); } catch (IllegalArgumentException expected) { }
            try { http.download(file, "sdcard", "test.gcode", p -> { }); fail(); } catch (IllegalArgumentException expected) { }
        } finally { file.delete(); }
    }
}
