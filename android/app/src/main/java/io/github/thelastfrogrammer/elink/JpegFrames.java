package io.github.thelastfrogrammer.elink;

import java.io.*;

/** Multipart-independent JPEG boundary reader, capped at 2 MiB per frame. */
public final class JpegFrames {
    private JpegFrames() { }
    public static byte[] next(InputStream input) throws IOException {
        int previous = -1, value, skipped = 0;
        ByteArrayOutputStream frame = null;
        while ((value = input.read()) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Camera stopped");
            if (frame == null) {
                if (++skipped > 2 * 1024 * 1024) throw new IOException("No JPEG frame");
                if (previous == 255 && value == 216) { frame = new ByteArrayOutputStream(); frame.write(255); frame.write(216); }
            } else {
                frame.write(value);
                if (frame.size() > 2 * 1024 * 1024) throw new IOException("Camera frame too large");
                if (previous == 255 && value == 217) return frame.toByteArray();
            }
            previous = value;
        }
        throw new EOFException("Camera stream ended");
    }
}
