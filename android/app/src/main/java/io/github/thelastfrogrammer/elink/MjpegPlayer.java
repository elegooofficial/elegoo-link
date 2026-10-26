package io.github.thelastfrogrammer.elink;

import android.graphics.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** One bounded JPEG at a time, no WebView scripts or background camera traffic. */
public final class MjpegPlayer implements AutoCloseable {
    public interface Listener { void frame(Bitmap image); void error(String message); }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    private volatile HttpURLConnection connection;
    public MjpegPlayer(String url, PrinterHttp.ConnectionFactory factory, Listener listener) {
        worker.execute(() -> {
            try {
                HttpURLConnection stream = factory.open(new URL(url)); connection = stream;
                if (closed) return;
                stream.setInstanceFollowRedirects(false); stream.setConnectTimeout(5000); stream.setReadTimeout(6000);
                if (stream.getResponseCode() != 200) throw new IOException("Camera HTTP rejected");
                String type = stream.getContentType();
                if (type == null || !(type.toLowerCase(java.util.Locale.ROOT).contains("multipart/x-mixed-replace") || type.toLowerCase(java.util.Locale.ROOT).contains("image/jpeg"))) throw new IOException("Not a JPEG stream");
                try (InputStream input = new BufferedInputStream(stream.getInputStream(), 32768)) {
                    while (!closed) {
                        byte[] bytes = JpegFrames.next(input);
                        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true; BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 4096 || bounds.outHeight > 4096) throw new IOException("Invalid camera frame");
                        BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 1;
                        while (bounds.outWidth / options.inSampleSize > 1920 || bounds.outHeight / options.inSampleSize > 1080) options.inSampleSize *= 2;
                        Bitmap frame = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                        if (frame == null) throw new IOException("Invalid JPEG");
                        CountDownLatch shown = new CountDownLatch(1);
                        main.post(() -> { try { if (!closed) listener.frame(frame); } finally { shown.countDown(); } });
                        if (!shown.await(2, TimeUnit.SECONDS)) throw new IOException("Camera view unavailable");
                        Thread.sleep(200);
                    }
                }
            } catch (Exception error) { if (!closed) main.post(() -> { if (!closed) listener.error("Camera unavailable. Check its current URL, selected connection route, and camera port. Stop and retry to reconnect."); }); }
            finally { HttpURLConnection stream = connection; if (stream != null) stream.disconnect(); connection = null; }
        });
    }
    public void close() { closed = true; HttpURLConnection stream = connection; if (stream != null) stream.disconnect(); worker.shutdownNow(); main.removeCallbacksAndMessages(null); }
}
