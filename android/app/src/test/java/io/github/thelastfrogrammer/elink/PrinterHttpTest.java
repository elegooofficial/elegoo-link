package io.github.thelastfrogrammer.elink;

import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exercises the actual uploader against a recording HTTP transport, including chunk boundaries. */
public class PrinterHttpTest {
    private static final List<RecordingConnection> requests = new ArrayList<>();
    private static int failureChunk = -1;
    @BeforeClass public static void installTransport() {
        URL.setURLStreamHandlerFactory(protocol -> "http".equals(protocol) ? new URLStreamHandler() {
            protected URLConnection openConnection(URL url) {
                RecordingConnection connection = new RecordingConnection(url, requests.size()); requests.add(connection); return connection;
            }
        } : null);
    }
    @Before public void reset() { requests.clear(); failureChunk = -1; }
    private static class RecordingConnection extends HttpURLConnection {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(); final int index;
        RecordingConnection(URL url, int index) { super(url); this.index = index; }
        public void connect() { }
        public void disconnect() { }
        public boolean usingProxy() { return false; }
        public OutputStream getOutputStream() { return bytes; }
        public int getResponseCode() { return 200; }
        public InputStream getInputStream() { return new ByteArrayInputStream((index == failureChunk ? "{\"error_code\":1000}" : "{\"error_code\":0}").getBytes(StandardCharsets.UTF_8)); }
    }
    @Test public void finalShortChunkHasExactRangeSameDigestAndToken() throws Exception {
        File file = File.createTempFile("contract", ".gcode");
        byte[] payload = new byte[1024 * 1024 + 17]; Arrays.fill(payload, (byte) 'G');
        try {
            try (OutputStream output = new FileOutputStream(file)) { output.write(payload); }
            List<Integer> progress = new ArrayList<>();
            new PrinterHttp("192.168.1.50", "token").upload(file, "test.gcode", progress::add);
            assertEquals(2, requests.size());
            assertEquals("bytes 0-1048575/1048593", requests.get(0).getRequestProperty("Content-Range"));
            assertEquals("bytes 1048576-1048592/1048593", requests.get(1).getRequestProperty("Content-Range"));
            assertEquals(17, requests.get(1).bytes.size());
            assertEquals("PUT", requests.get(0).getRequestMethod());
            assertEquals("test.gcode", requests.get(0).getRequestProperty("X-File-Name"));
            assertEquals("token", requests.get(0).getRequestProperty("X-Token"));
            assertEquals(requests.get(0).getRequestProperty("X-File-MD5"), requests.get(1).getRequestProperty("X-File-MD5"));
            assertFalse(requests.get(0).getInstanceFollowRedirects());
            assertEquals(Integer.valueOf(100), progress.get(progress.size() - 1));
        } finally { file.delete(); }
    }
    @Test public void rejectedChunkDoesNotAdvanceOrSendNextChunk() throws Exception {
        File file = File.createTempFile("contract", ".gcode");
        try {
            try (RandomAccessFile output = new RandomAccessFile(file, "rw")) { output.setLength(2 * 1024 * 1024); }
            failureChunk = 0;
            List<Integer> progress = new ArrayList<>();
            try { new PrinterHttp("192.168.1.50", "bad").upload(file, "test.gcode", progress::add); fail(); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("1000")); }
            assertEquals(1, requests.size()); assertTrue(progress.isEmpty());
        } finally { file.delete(); }
    }
    @Test public void cancellationBeforeUploadSendsNothing() throws Exception {
        File file = File.createTempFile("contract", ".gcode");
        try {
            try (OutputStream output = new FileOutputStream(file)) { output.write(1); }
            PrinterHttp client = new PrinterHttp("192.168.1.50", "token"); client.cancel();
            try { client.upload(file, "test.gcode", ignored -> {}); fail(); } catch (IOException expected) { }
            assertTrue(requests.isEmpty());
        } finally { file.delete(); }
    }
    @Test public void cancelAfterAcknowledgedChunkStopsBeforeNextChunk() throws Exception {
        File file=File.createTempFile("contract", ".gcode");
        try {
            try(RandomAccessFile output=new RandomAccessFile(file,"rw")) {output.setLength(2*1024*1024);}
            PrinterHttp client=new PrinterHttp("192.168.1.50","token");List<Integer> progress=new ArrayList<>();
            try {client.upload(file,"test.gcode",percent -> {progress.add(percent);client.cancel();});fail("Cancelled upload must not complete");}
            catch(IOException expected) {assertTrue(expected.getMessage().contains("cancelled"));}
            assertEquals(1,requests.size());assertEquals(Collections.singletonList(50),progress);
        }finally{file.delete();}
    }

}
