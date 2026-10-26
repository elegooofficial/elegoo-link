package io.github.thelastfrogrammer.elink;

import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.*;
import java.util.zip.CRC32;

/** Reads comment-wrapped slicer images only. Never opens a URL or executes G-code. */
public final class EmbeddedThumbnail {
    public static final int MAX_BYTES = 256 * 1024, MAX_DIMENSION = 1024, MAX_PIXELS = 1024 * 1024;
    private static final int MAX_ENCODED = 4 * ((MAX_BYTES + 2) / 3), MAX_BLOCKS = 16;
    private static final Pattern BEGIN = Pattern.compile("(thumbnail(?:_[A-Za-z0-9]+)?) begin ([0-9]+)x([0-9]+) ([0-9]+)", Pattern.CASE_INSENSITIVE);
    public final int width, height;
    public final String format;
    private final byte[] bytes;
    private EmbeddedThumbnail(byte[] bytes, int width, int height, String format) { this.bytes = bytes; this.width = width; this.height = height; this.format = format; }
    public byte[] data() { return bytes.clone(); }
    public int size() { return bytes.length; }
    public static boolean validDimensions(int width, int height) { return width > 0 && height > 0 && width <= MAX_DIMENSION && height <= MAX_DIMENSION && (long) width * height <= MAX_PIXELS; }
    public static final class Parser {
        private String kind;
        private int width, height, expected, blocks;
        private final StringBuilder encoded = new StringBuilder();
        private boolean invalid, limited;
        private int rejected;
        private EmbeddedThumbnail best;
        public EmbeddedThumbnail best() { return best; }
        public String note() { return (best == null ? "No supported embedded preview found." : "Embedded " + best.format + " preview: " + best.width + "×" + best.height) + (rejected > 0 ? " Invalid/unsupported thumbnail blocks skipped: " + rejected + "." : "") + (limited ? " Thumbnail scan limited to 16 blocks." : ""); }
        public void overlongLine() { if (kind != null) invalid = true; }
        public void finish() { if (kind != null) { rejected++; reset(); } }
        public boolean accept(String line) {
            if (!line.startsWith(";")) { if (kind != null) { rejected++; reset(); } return false; }
            String comment = line.substring(1).trim(); Matcher match = BEGIN.matcher(comment);
            if (match.matches()) {
                if (kind != null) rejected++;
                reset(); kind = match.group(1).toLowerCase(Locale.ROOT); blocks++;
                try {
                    width = Integer.parseInt(match.group(2)); height = Integer.parseInt(match.group(3)); expected = Integer.parseInt(match.group(4));
                    invalid = !validDimensions(width, height) || expected < 4 || expected > MAX_ENCODED || expected % 4 != 0
                        || !(kind.equals("thumbnail") || kind.equals("thumbnail_png") || kind.equals("thumbnail_jpg")) || blocks > MAX_BLOCKS;
                } catch (NumberFormatException error) { invalid = true; }
                if (blocks > MAX_BLOCKS) limited = true;
                return true;
            }
            if (kind == null) return false;
            if (comment.equalsIgnoreCase(kind + " end")) {
                try {
                    if (invalid || encoded.length() != expected) throw new IOException("Invalid thumbnail block");
                    byte[] bytes = Base64.getDecoder().decode(encoded.toString());
                    EmbeddedThumbnail image = validate(bytes, width, height, kind);
                    if (best == null || (long) image.width * image.height > (long) best.width * best.height) best = image;
                } catch (Exception error) { rejected++; }
                reset(); return true;
            }
            if (!invalid) {
                if (encoded.length() + comment.length() > expected || !comment.matches("[A-Za-z0-9+/=]*")) invalid = true;
                else encoded.append(comment);
            }
            return true;
        }
        private void reset() { kind = null; encoded.setLength(0); invalid = false; width = height = expected = 0; }
    }
    private static EmbeddedThumbnail validate(byte[] data, int declaredWidth, int declaredHeight, String kind) throws IOException {
        if (data.length > MAX_BYTES) throw new IOException("Image too large");
        int[] dimensions; String format;
        if (data.length >= 8 && data[0] == (byte)137 && data[1] == 80 && data[2] == 78 && data[3] == 71 && data[4] == 13 && data[5] == 10 && data[6] == 26 && data[7] == 10) { dimensions = png(data); format = "PNG"; }
        else if (data.length >= 4 && data[0] == (byte)255 && data[1] == (byte)216) { dimensions = jpeg(data); format = "JPEG"; }
        else throw new IOException("Unsupported image");
        if (!validDimensions(dimensions[0], dimensions[1]) || dimensions[0] != declaredWidth || dimensions[1] != declaredHeight
            || kind.equals("thumbnail_png") && !format.equals("PNG") || kind.equals("thumbnail_jpg") && !format.equals("JPEG")) throw new IOException("Image declaration mismatch");
        return new EmbeddedThumbnail(data, dimensions[0], dimensions[1], format);
    }
    private static long u32(byte[] data, int i) { return ((long)(data[i] & 255) << 24) | ((long)(data[i+1] & 255) << 16) | ((long)(data[i+2] & 255) << 8) | (data[i+3] & 255); }
    private static int u16(byte[] data, int i) { return ((data[i] & 255) << 8) | (data[i+1] & 255); }
    private static int[] png(byte[] data) throws IOException {
        int position = 8, width = 0, height = 0, chunks = 0; boolean pixels = false;
        while (position + 12 <= data.length && chunks++ < 1024) {
            long length = u32(data, position); if (length > data.length - position - 12) throw new IOException("Truncated PNG");
            int count = (int)length; String type = new String(data, position+4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            CRC32 crc = new CRC32(); crc.update(data, position+4, count+4); if (crc.getValue() != u32(data,position+8+count)) throw new IOException("PNG checksum mismatch");
            if (chunks == 1) { if (!type.equals("IHDR") || count != 13) throw new IOException("Missing PNG header"); width = (int)u32(data,position+8); height = (int)u32(data,position+12); }
            else if (type.equals("IHDR")) throw new IOException("Duplicate PNG header");
            if (type.equals("IDAT")) pixels = true;
            position += count+12;
            if (type.equals("IEND")) { if (count != 0 || !pixels || position != data.length) throw new IOException("Invalid PNG ending"); return new int[] {width,height}; }
        }
        throw new IOException("Incomplete PNG");
    }
    private static int[] jpeg(byte[] data) throws IOException {
        if (data[data.length-2] != (byte)255 || data[data.length-1] != (byte)217) throw new IOException("Incomplete JPEG");
        int position = 2, width = 0, height = 0;
        while (position+1 < data.length) {
            if ((data[position++] & 255) != 255) throw new IOException("Invalid JPEG marker");
            while (position < data.length && (data[position] & 255) == 255) position++;
            if (position >= data.length) break; int marker = data[position++] & 255;
            if (marker == 0xDA) { if (width == 0 || position+2 > data.length || u16(data,position) < 2 || position+u16(data,position) > data.length-2) break; return new int[] {width,height}; }
            if (marker == 0xD9 || marker == 0 || marker == 0xD8) break;
            if (marker == 1 || marker >= 0xD0 && marker <= 0xD7) continue;
            if (position+2 > data.length) break; int length = u16(data,position); if (length < 2 || position+length > data.length) break;
            if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2) { if (length < 8 || width != 0) break; height = u16(data,position+3); width = u16(data,position+5); }
            position += length;
        }
        throw new IOException("Invalid JPEG dimensions");
    }
}
