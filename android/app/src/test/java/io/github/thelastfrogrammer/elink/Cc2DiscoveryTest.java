package io.github.thelastfrogrammer.elink;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class Cc2DiscoveryTest {
    @Test public void acceptsUpstreamReplyWithOrWithoutMethod() {
        assertEquals("F014J0D52ELR818", Cc2Discovery.serial("{\"id\":0,\"result\":{\"sn\":\"F014J0D52ELR818\",\"lan_status\":true}}"));
        assertEquals("TEST-CC2", Cc2Discovery.serial("{\"id\":0,\"method\":7000,\"result\":{\"sn\":\"TEST-CC2\"}}"));
    }
    @Test public void flagsAcceptOnlyBooleansOrNumericZeroAndOne() {
        Cc2Discovery.Info info = Cc2Discovery.parse("{\"id\":0,\"result\":{\"sn\":\"TEST\",\"lan_status\":1,\"token_status\":false}}");
        assertEquals(Boolean.TRUE, info.lanOnly); assertEquals(Boolean.FALSE, info.codeProtected);
        info = Cc2Discovery.parse("{\"id\":0,\"result\":{\"sn\":\"TEST\",\"lan_status\":\"0\",\"token_status\":2}}");
        assertNull(info.lanOnly); assertNull(info.codeProtected); assertTrue(info.summary().contains("not reported"));
    }
    @Test public void rejectsEchoWrongMethodMissingOrUnsafeIdentity() {
        for (String payload : Arrays.asList("{}", "{\"id\":0,\"method\":7000}", "{\"id\":1,\"result\":{\"sn\":\"TEST\"}}",
            "{\"id\":0,\"method\":1002,\"result\":{\"sn\":\"TEST\"}}", "{\"id\":0,\"result\":{\"sn\":123}}",
            "{\"id\":0,\"result\":{\"sn\":\"other/topic\"}}", "{\"id\":0,\"result\":{\"sn\":\"TEST\",\"error_code\":1000}}"))
            assertNull(payload, Cc2Discovery.serial(payload));
    }
    @Test public void sendsReadOnlyDiscoveryAndIgnoresEchoBeforeValidReply() throws Exception {
        try (DatagramSocket printer = new DatagramSocket(0, InetAddress.getLoopbackAddress());
             Cc2Discovery discovery = new Cc2Discovery(DatagramSocket::new, Collections.emptyList(), printer.getLocalPort(), 1500)) {
            CompletableFuture<Void> server = CompletableFuture.runAsync(() -> {
                try {
                    printer.setSoTimeout(1500); DatagramPacket request = new DatagramPacket(new byte[2048], 2048); printer.receive(request);
                    assertEquals("{\"id\":0,\"method\":7000}", new String(request.getData(), 0, request.getLength(), StandardCharsets.UTF_8));
                    printer.send(new DatagramPacket(request.getData(), request.getLength(), request.getAddress(), request.getPort()));
                    byte[] reply = "{\"id\":0,\"result\":{\"sn\":\"TEST-CC2\"}}".getBytes(StandardCharsets.UTF_8);
                    printer.send(new DatagramPacket(reply, reply.length, request.getAddress(), request.getPort()));
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            assertEquals("TEST-CC2", discovery.discover("127.0.0.1")); server.get(2, TimeUnit.SECONDS);
        }
    }
    @Test public void cancellationClosesReceiveWithoutWaitingForTimeout() throws Exception {
        CountDownLatch receiving = new CountDownLatch(1);
        Cc2Discovery discovery = new Cc2Discovery(() -> { DatagramSocket socket = new DatagramSocket(); receiving.countDown(); return socket; }, Collections.emptyList(), 52700, 10000);
        CompletableFuture<Boolean> result = CompletableFuture.supplyAsync(() -> {
            try { discovery.discover("127.0.0.1"); return false; } catch (java.io.IOException expected) { return true; }
        });
        try { assertTrue(receiving.await(1, TimeUnit.SECONDS)); discovery.close(); assertTrue(result.get(1, TimeUnit.SECONDS)); }
        finally { discovery.close(); }
    }
    @Test public void broadcastsCannotSelectAnotherPrintersIdentity() throws Exception {
        try (DatagramSocket target = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
             DatagramSocket other = new DatagramSocket(0, InetAddress.getByName("127.0.0.2"));
             Cc2Discovery discovery = new Cc2Discovery(DatagramSocket::new, Collections.emptyList(), target.getLocalPort(), 250)) {
            CompletableFuture<Void> server = CompletableFuture.runAsync(() -> {
                try {
                    target.setSoTimeout(1000); DatagramPacket request = new DatagramPacket(new byte[2048], 2048); target.receive(request);
                    byte[] reply = "{\"id\":0,\"result\":{\"sn\":\"WRONG-PRINTER\"}}".getBytes(StandardCharsets.UTF_8);
                    other.send(new DatagramPacket(reply, reply.length, request.getAddress(), request.getPort()));
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            try { discovery.discover("127.0.0.1"); fail("Another printer cannot supply the identity"); }
            catch (SocketTimeoutException expected) { }
            server.get(2, TimeUnit.SECONDS);
        }
    }
}
