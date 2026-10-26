package io.github.thelastfrogrammer.elink;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/** Run on the file worker; verify native dimensions before allocating preview pixels. */
public final class ThumbnailDecoder {
    private ThumbnailDecoder() { }
    public static Bitmap decode(EmbeddedThumbnail thumbnail) {
        if (thumbnail == null) return null;
        try {
            byte[] bytes = thumbnail.data(); BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (!EmbeddedThumbnail.validDimensions(options.outWidth,options.outHeight) || options.outWidth != thumbnail.width || options.outHeight != thumbnail.height) return null;
            options.inJustDecodeBounds = false; options.inSampleSize = 1;
            while (Math.max(options.outWidth,options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        } catch (RuntimeException error) { return null; }
    }
}
