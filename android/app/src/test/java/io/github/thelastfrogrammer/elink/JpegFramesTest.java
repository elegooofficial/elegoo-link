package io.github.thelastfrogrammer.elink;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class JpegFramesTest {
    @Test public void extractsConsecutiveFramesAcrossMultipartHeaders() throws Exception {
        byte[] frame={(byte)255,(byte)216,1,2,3,(byte)255,(byte)217}; ByteArrayOutputStream out=new ByteArrayOutputStream(); out.write("--boundary\r\nContent-Type: image/jpeg\r\n\r\n".getBytes("UTF-8")); out.write(frame); out.write("\r\n--boundary\r\n\r\n".getBytes("UTF-8")); out.write(frame);
        InputStream input=new ByteArrayInputStream(out.toByteArray()); assertArrayEquals(frame,JpegFrames.next(input)); assertArrayEquals(frame,JpegFrames.next(input));
    }
    @Test(expected=EOFException.class) public void truncatedFrameFailsInsteadOfReturningPartialImage() throws Exception { JpegFrames.next(new ByteArrayInputStream(new byte[] {(byte)255,(byte)216,1,2})); }
    @Test(expected=IOException.class) public void oversizedFrameIsBounded() throws Exception { byte[] b=new byte[2*1024*1024+1]; b[0]=(byte)255;b[1]=(byte)216;JpegFrames.next(new ByteArrayInputStream(b)); }
    @Test(expected=IOException.class) public void missingBoundaryIsBounded() throws Exception { JpegFrames.next(new ByteArrayInputStream(new byte[2*1024*1024+1])); }
}
