package com.ariyaan.Demo.Service;

import com.ariyaan.Demo.Repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
    private final JdbcTemplate db;
    private final UserRepository users;
    private final RequestLimits limits;
    private final PasswordEncoder passwords;
    public PasswordResetService(JdbcTemplate db, UserRepository users, RequestLimits limits, PasswordEncoder passwords) {
        this.db=db; this.users=users; this.limits=limits; this.passwords=passwords;
    }
    private String email(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    @Transactional
    public void request(String address) {
        String email=email(address); limits.check("reset-send",email,3);
        var user=users.findByEmailForUpdate(email).filter(u->u.isEnabled()).orElse(null);
        if (user==null) return;
        String otp=String.format(Locale.ROOT,"%06d",new SecureRandom().nextInt(1000000));
        db.update("""
            INSERT INTO password_resets(user_id,otp,expires_at) VALUES(?,?,now()+interval '15 minutes')
            ON CONFLICT(user_id) DO UPDATE SET otp=EXCLUDED.otp, expires_at=EXCLUDED.expires_at,
            verified_at=NULL,token_hash=NULL,used_at=NULL,requested_at=now()
            """,user.getId(),otp);
        db.update("UPDATE reset_mail_jobs SET state='CANCELLED' WHERE user_id=? AND state IN ('PENDING','SENDING')",user.getId());
        db.update("INSERT INTO reset_mail_jobs(id,user_id,email,otp,expires_at) VALUES(?,?,?,?,now()+interval '15 minutes')",
                UUID.randomUUID(),user.getId(),user.getEmail(),otp);
    }
    @Transactional
    public Map<String,Object> verify(String address,String otp) {
        String email=email(address); limits.check("reset-verify",email,10);
        var user=users.findByEmailForUpdate(email).orElseThrow(this::invalid);
        if(!user.isEnabled()) throw invalid();
        var rows=db.queryForList("SELECT * FROM password_resets WHERE user_id=? AND expires_at>now() AND verified_at IS NULL AND used_at IS NULL FOR UPDATE",user.getId());
        if (rows.isEmpty() || !MessageDigest.isEqual(otp.getBytes(StandardCharsets.UTF_8), rows.get(0).get("otp").toString().getBytes(StandardCharsets.UTF_8))) throw invalid();
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        db.update("UPDATE password_resets SET verified_at=now(),token_hash=?,expires_at=now()+interval '10 minutes' WHERE user_id=?",hash(token),user.getId());
        return Map.of("resetToken",token,"expiresInSeconds",600);
    }
    @Transactional
    public void reset(String address,String token,String password) {
        String email=email(address); limits.check("reset-finish",email,10);
        if(password.getBytes(StandardCharsets.UTF_8).length>72) throw new IllegalArgumentException("Password must use at most 72 UTF-8 bytes");
        if (!token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        var user=users.findByEmailForUpdate(email).orElseThrow(this::invalid);
        int used=db.update("UPDATE password_resets SET used_at=now() WHERE user_id=? AND token_hash=? AND verified_at IS NOT NULL AND used_at IS NULL AND expires_at>now()",
                user.getId(),hash(token));
        if (used!=1 || !user.isEnabled()) throw invalid();
        user.setPassword(passwords.encode(password));user.setCredentialVersion(user.getCredentialVersion()+1);users.save(user);
        db.update("UPDATE push_devices SET active=false WHERE user_id=?",user.getId());
    }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("Reset code or session is invalid or expired"); }
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
