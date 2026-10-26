package com.ariyaan.Demo;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "app.jwt.secret=payment-context-test-key-at-least-32-bytes",
    "spring.flyway.enabled=false", "razorpay.enabled=false", "razorpay.route-enabled=false"
})
class PaymentApplicationContextTest {
    @LocalServerPort int port;
    HttpResponse<String> request(String path, boolean post) throws Exception {
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));
        if(post) req.POST(HttpRequest.BodyPublishers.ofString("{}")) .header("Content-Type","application/json");
        return HttpClient.newHttpClient().send(req.build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void checkoutLoadsButPaymentAndTokenBypassAreProtected() throws Exception {
        assertEquals(200,request("/payment-checkout.html",false).statusCode());
        assertTrue(request("/api/payments/rides/42/order",true).statusCode()>=400);
        assertTrue(request("/sendJWT?username=someone",false).statusCode()>=400);
    }
    @Test void unsignedWebhookIsRejectedAsBadRequest() throws Exception {
        assertEquals(400,request("/api/payments/razorpay/webhook",true).statusCode());
    }
}
