package com.ariyaan.Demo.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RazorpayClient {
    private final String keyId, keySecret, webhookSecret;
    private final boolean enabled, routeEnabled;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public RazorpayClient(@Value("${razorpay.key-id:}") String keyId,
            @Value("${razorpay.key-secret:}") String keySecret,
            @Value("${razorpay.webhook-secret:}") String webhookSecret,
            @Value("${razorpay.enabled:false}") boolean enabled,
            @Value("${razorpay.route-enabled:false}") boolean routeEnabled) {
        this.keyId = keyId; this.keySecret = keySecret; this.webhookSecret = webhookSecret;
        this.enabled = enabled; this.routeEnabled = routeEnabled;
    }
    public boolean enabled() { return enabled; }
    public boolean routeEnabled() { return enabled && routeEnabled; }
    public String keyId() { requireEnabled(); return keyId; }
    public void requireEnabled() {
        if (!enabled || keyId.isBlank() || keySecret.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Razorpay is not configured");
    }
    public void verifyCheckout(String storedOrderId, String paymentId, String signature) {
        requireEnabled();
        if (!signatureMatches(keySecret, storedOrderId + "|" + paymentId, signature))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment signature");
    }
    public void verifyWebhook(String body, String signature) {
        requireEnabled();
        if (webhookSecret.isBlank() || !signatureMatches(webhookSecret, body, signature))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature");
    }
    public static boolean signatureMatches(String secret, String message, String signature) {
        if (secret == null || secret.isBlank() || signature == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)),
                    HexFormat.of().parseHex(signature));
        } catch (IllegalArgumentException ex) { return false; }
        catch (Exception ex) { throw new IllegalStateException("Unable to verify payment signature", ex); }
    }
    public JsonNode get(String path) { return request("GET", path, null); }
    public JsonNode post(String path, Map<String, Object> body) { return request("POST", path, body); }
    private JsonNode request(String method, String path, Map<String, Object> body) {
        requireEnabled();
        if (!path.matches("/[a-zA-Z0-9_/?=&.-]+")) throw new IllegalArgumentException("Invalid Razorpay path");
        try {
            String auth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1" + path))
                    .timeout(Duration.ofSeconds(20)).header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json");
            if ("POST".equals(method)) request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            else request.GET();
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay request failed; status " + response.statusCode());
            return json.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay request interrupted");
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) {
            // Never log response bodies, authentication headers or bank details.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay request unavailable; reconcile before retrying");
        }
    }
}
