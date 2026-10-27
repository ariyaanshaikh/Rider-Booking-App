package com.ariyaan.Demo.Service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Database counters are shared across restarts and instances. No OTPs are stored here. */
@Service
public class RequestLimits {
    private final JdbcTemplate db;
    public RequestLimits(JdbcTemplate db) { this.db=db; }
    @Transactional(propagation=Propagation.REQUIRES_NEW, noRollbackFor=TooManyRequests.class)
    public void check(String purpose, String identity, int maximum) {
        if (identity == null || identity.length() > 255) throw new IllegalArgumentException("Invalid account identifier");
        Integer count=db.queryForObject("""
            INSERT INTO request_limits(purpose,identity,window_start,attempts)
            VALUES (?,?,to_timestamp(floor(extract(epoch from now())/900)*900),1)
            ON CONFLICT (purpose,identity) DO UPDATE SET
            attempts=CASE WHEN request_limits.window_start=EXCLUDED.window_start
                THEN LEAST(request_limits.attempts+1,10000) ELSE 1 END,window_start=EXCLUDED.window_start
            RETURNING attempts
            """, Integer.class,purpose,identity.trim().toLowerCase(java.util.Locale.ROOT));
        if (count != null && count > maximum) throw new TooManyRequests();
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=3600000, initialDelay=3600000)
    public void cleanup() { db.update("DELETE FROM request_limits WHERE window_start < now()-interval '1 day'"); }
    public static class TooManyRequests extends ResponseStatusException {
        public TooManyRequests() { super(HttpStatus.TOO_MANY_REQUESTS,"Too many attempts; try again later"); }
    }
}
