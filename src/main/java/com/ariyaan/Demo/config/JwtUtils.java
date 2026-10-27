package com.ariyaan.Demo.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtils {

    private final Key key;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.ariyaan.Demo.Repository.UserRepository users;
    public JwtUtils(@org.springframework.beans.factory.annotation.Value("${app.jwt.secret}") String secret) {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        key = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claimsResolver.apply(claims);
    }

    public String generateToken(String username) {
    	long thirtyDaysInMillis = 1000L * 60 * 60 * 24 * 30;
     /*   return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10 hours
                .signWith(key, SignatureAlgorithm.HS256)
                .compact(); */
    	return Jwts.builder()
                .setSubject(username)
                .claim("cv", users==null ? 0 : users.findByUserName(username).map(com.ariyaan.Demo.Entity.User::getCredentialVersion).orElse(0))
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + thirtyDaysInMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        Number version=extractClaim(token,claims -> claims.get("cv",Number.class));
        int current=userDetails instanceof com.ariyaan.Demo.Entity.User user ? user.getCredentialVersion() : 0;
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token)
                && (version==null ? 0 : version.intValue())==current;
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }
}
