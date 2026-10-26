package io.github.thelastfrogrammer.elink;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public class EmbeddedThumbnailTest {
    static byte[] png(int width,int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write(new byte[] {(byte)137,80,78,71,13,10,26,10});
        ByteArrayOutputStream header = new ByteArrayOutputStream(); DataOutputStream h = new DataOutputStream(header); h.writeInt(width);h.writeInt(height);h.write(new byte[] {8,2,0,0,0}); chunk(out,"IHDR",header.toByteArray());
        ByteArrayOutputStream pixels = new ByteArrayOutputStream(); try(DeflaterOutputStream d = new DeflaterOutputStream(pixels)) { for(int y=0;y<height;y++){ d.write(0); for(int x=0;x<width;x++)d.write(new byte[] {30,100,(byte)170}); } }
        chunk(out,"IDAT",pixels.toByteArray());chunk(out,"IEND",new byte[0]);return out.toByteArray();
    }
    private static void chunk(OutputStream out,String type,byte[] data) throws Exception {
        DataOutputStream d=new DataOutputStream(out);byte[] t=type.getBytes(StandardCharsets.US_ASCII);d.writeInt(data.length);d.write(t);d.write(data);CRC32 crc=new CRC32();crc.update(t);crc.update(data);d.writeInt((int)crc.getValue());
    }
    static String block(String kind,int width,int height,byte[] bytes) {
        String data=Base64.getEncoder().encodeToString(bytes);StringBuilder text=new StringBuilder("; "+kind+" begin "+width+"x"+height+" "+data.length()+"\n");
        for(int i=0;i<data.length();i+=76)text.append("; ").append(data, i, Math.min(data.length(),i+76)).append('\n');
        return text.append("; ").append(kind).append(" end\n").toString();
    }
    private EmbeddedThumbnail.Parser parse(String text) { EmbeddedThumbnail.Parser p=new EmbeddedThumbnail.Parser();for(String line:text.split("\n"))p.accept(line.trim());p.finish();return p; }
    @Test public void extractsWrappedPngAndDefensivelyCopiesPixels() throws Exception {
        byte[] bytes=png(2,3);EmbeddedThumbnail.Parser p=parse(block("thumbnail",2,3,bytes));EmbeddedThumbnail t=p.best();assertNotNull(t);assertEquals(2,t.width);assertEquals(3,t.height);assertEquals("PNG",t.format);assertArrayEquals(bytes,t.data());byte[] copy=t.data();copy[0]=0;assertEquals((byte)137,t.data()[0]);
    }
    @Test public void choosesLargestValidCandidateAndSkipsUnsupportedFormat() throws Exception {
        EmbeddedThumbnail.Parser p=parse(block("thumbnail",1,1,png(1,1))+block("thumbnail_QOI",2,3,png(2,3))+block("thumbnail_PNG",2,3,png(2,3)));assertEquals(2,p.best().width);assertTrue(p.note().contains("skipped: 1"));
    }
    @Test public void extractsRealJpegWithHeaderDimensions() throws Exception {
        byte[] bytes=Base64.getDecoder().decode("/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAADAAIDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDlqKKK+qPmz//Z");EmbeddedThumbnail t=parse(block("thumbnail_JPG",2,3,bytes)).best();assertNotNull(t);assertEquals("JPEG",t.format);assertArrayEquals(bytes,t.data());
    }
    @Test public void badCountAndMissingEndNeverBecomePreview() throws Exception {
        String block=block("thumbnail",1,1,png(1,1));String bad=block.replaceFirst("1x1 [0-9]+","1x1 4");assertNull(parse(bad).best());assertNull(parse(block.replace("; thumbnail end\n","")).best());
    }
    @Test public void rejectsCorruptPngTruncatedJpegAndWrongFormat() throws Exception {
        byte[] bytes=png(1,1);bytes[bytes.length-1]^=1;assertNull(parse(block("thumbnail",1,1,bytes)).best());
        byte[] jpeg=Base64.getDecoder().decode("/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAADAAIDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDlqKKK+qPmz//Z");assertNull(parse(block("thumbnail_JPG",2,3,Arrays.copyOf(jpeg,jpeg.length-2))).best());assertNull(parse(block("thumbnail_JPG",1,1,png(1,1))).best());
    }
    @Test public void imageDimensionDisagreementAndOversizedDeclarationAreRejected() throws Exception {
        assertNull(parse(block("thumbnail",2,1,png(1,1))).best());assertNull(parse("; thumbnail begin 999999999999x1 4\n; AAAA\n; thumbnail end").best());
        assertNull(parse("; thumbnail begin 1025x1 4\n; AAAA\n; thumbnail end").best());assertFalse(EmbeddedThumbnail.validDimensions(1024,1025));
        assertNull(parse("; thumbnail begin 1x1 999999999\n; AAAA\n; thumbnail end").best());
    }
    @Test public void invalidBase64AndUnrelatedPayloadCannotPoisonFollowingBlock() throws Exception {
        EmbeddedThumbnail.Parser p=parse("; thumbnail begin 1x1 4\n; ____\n; thumbnail end\n"+block("thumbnail",1,1,png(1,1)));assertNotNull(p.best());assertTrue(p.note().contains("skipped: 1"));
    }
    @Test public void nestedBlockRestartsSafelyAndOverlongPayloadIsRejected() throws Exception {
        EmbeddedThumbnail.Parser p=parse("; thumbnail begin 1x1 4\n"+block("thumbnail",1,1,png(1,1)));assertNotNull(p.best());assertTrue(p.note().contains("skipped: 1"));
        p=new EmbeddedThumbnail.Parser();p.accept("; thumbnail begin 1x1 4");p.overlongLine();p.accept("; AAAA");p.accept("; thumbnail end");assertNull(p.best());
    }
    @Test public void candidateLimitIsEnforcedWithoutLosingExistingPreview() throws Exception {
        StringBuilder text=new StringBuilder(block("thumbnail",1,1,png(1,1)));for(int i=0;i<16;i++)text.append(block("thumbnail",2,3,png(2,3)));
        EmbeddedThumbnail.Parser p=parse(text.toString());assertNotNull(p.best());assertTrue(p.note().contains("limited"));
    }
    @Test public void nonCommentCodeTerminatesIncompleteBlockAndIsNotConsumed() {
        EmbeddedThumbnail.Parser p=new EmbeddedThumbnail.Parser();p.accept("; thumbnail begin 1x1 4");assertFalse(p.accept("T2"));assertNull(p.best());assertTrue(p.note().contains("skipped"));
    }
}
