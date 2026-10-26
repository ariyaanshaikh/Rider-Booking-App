package com.ariyaan.Demo;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.ariyaan.Demo.Service.RazorpayClient;

class RazorpaySecurityTest {
    private static final String HMAC = "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8";
    @Test void validatesKnownHmacSha256Vector() {
        assertTrue(RazorpayClient.signatureMatches("key", "The quick brown fox jumps over the lazy dog", HMAC));
    }
    @Test void rejectsTamperingMissingAndMalformedSignatures() {
        assertFalse(RazorpayClient.signatureMatches("key", "tampered body", HMAC));
        assertFalse(RazorpayClient.signatureMatches("wrong secret", "The quick brown fox jumps over the lazy dog", HMAC));
        assertFalse(RazorpayClient.signatureMatches("key", "body", null));
        assertFalse(RazorpayClient.signatureMatches("key", "body", "invalid hex"));
        assertFalse(RazorpayClient.signatureMatches("", "body", HMAC));
    }
    @Test void disabledConfigurationCannotCallProviderOrReleaseTransfers() {
        var client = new RazorpayClient("", "", "", false, false);
        assertFalse(client.routeEnabled());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, client::requireEnabled);
    }
}
