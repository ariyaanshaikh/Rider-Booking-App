package com.ariyaan.Demo;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import com.ariyaan.Demo.Service.ExpoPushClient;

class ExpoPushClientTest {
    @Test void usesExpoTicketAndReceiptProtocolWithoutExternalPush() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var body=new AtomicReference<String>();
        server.createContext("/send",exchange->{
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] response="{\"data\":{\"status\":\"ok\",\"id\":\"ticket-test\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });
        server.createContext("/getReceipts",exchange->{
            byte[] response="{\"data\":{\"ticket-test\":{\"status\":\"ok\"}}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });
        server.start();
        try {
            var client=new ExpoPushClient("http://127.0.0.1:"+server.getAddress().getPort(),"test-token");
            assertEquals("ticket-test",client.send("ExponentPushToken[test123456]",Map.of("eventId","event-test","type","RIDE_REQUEST","rideId",42,"remote",true),300).id());
            assertTrue(body.get().contains("ride-requests"));assertFalse(body.get().contains("otp"));
            assertEquals("ok",client.receipt("ticket-test").status());
            assertEquals("pending",client.receipt("not-yet-ready").status());
        } finally { server.stop(0); }
    }
}
