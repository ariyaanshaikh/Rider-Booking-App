package com.ariyaan.Demo.Service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** A short-lived ride-scoped capability. Account JWTs never enter browser URLs. */
@Service
public class CheckoutSessions {
    public record Session(int rideId, String username) {}
    private final JdbcTemplate db;
    private final RidePaymentStore store;
    private final RazorpayClient provider;
    private final String baseUrl;
    public CheckoutSessions(JdbcTemplate db, RidePaymentStore store, RazorpayClient provider,
            @Value("${app.public-base-url:http://localhost:8088}") String baseUrl) {
        this.db=db; this.store=store; this.provider=provider; this.baseUrl=baseUrl.replaceAll("/+$", "");
    }
    public Map<String,Object> create(int rideId, String username) {
        provider.requireEnabled(); store.authorize(rideId,username,true);
        byte[] random=new byte[32]; new SecureRandom().nextBytes(random);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        db.update("INSERT INTO checkout_sessions(token_hash,ride_id,username,expires_at) VALUES (?,?,?,now()+interval '10 minutes')",
                hash(token),rideId,username);
        return Map.of("checkoutUrl",baseUrl+"/payment-checkout.html#session="+token,"expiresInSeconds",600);
    }
    public Session resolve(String token) {
        if (token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Checkout session is invalid or expired");
        var sessions=db.query("SELECT ride_id,username FROM checkout_sessions WHERE token_hash=? AND expires_at>now()",
                (rs,n)->new Session(rs.getInt(1),rs.getString(2)),hash(token));
        if (sessions.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Checkout session is invalid or expired");
        return sessions.get(0);
    }
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=3600000,initialDelay=3600000)
    public void cleanup() { db.update("DELETE FROM checkout_sessions WHERE expires_at<now()"); }
}
