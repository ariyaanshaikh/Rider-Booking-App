package com.ariyaan.Demo.Service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Account-scoped diagnostics; never returns device tokens, credentials or ride data. */
@Service
public class NotificationReadiness {
    private final JdbcTemplate db;
    private final boolean pushEnabled, workersEnabled;
    public NotificationReadiness(JdbcTemplate db,
            @Value("${app.push.enabled:false}") boolean pushEnabled,
            @Value("${app.background-workers.enabled:true}") boolean workersEnabled) {
        this.db=db; this.pushEnabled=pushEnabled; this.workersEnabled=workersEnabled;
    }
    public boolean storageReady() {
        try {
            // WHERE false validates the deployed schema without reading any
            // account data. Existence alone misses partial/legacy migrations.
            db.queryForList("""
                SELECT d.app,d.installation_id,d.user_id,d.token,d.platform,d.active,d.updated_at,
                    n.id,n.user_id,n.event_key,n.type,n.ride_id,n.created_at,
                    j.id,j.notification_id,j.app,j.installation_id,j.token,j.state,j.attempts,
                    j.available_at,j.lease_until,j.expires_at,j.receipt_id,j.last_error,j.created_at,
                    l.purpose,l.identity,l.window_start,l.attempts
                FROM push_devices d, notification_inbox n, push_jobs j, request_limits l
                WHERE false
                """);
            return true;
        } catch (DataAccessException ex) { return false; }
    }
    public void requireStorage() {
        if (!storageReady()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Notification storage is unavailable. Deploy the production database migrations and retry.");
    }
    public Map<String,Object> status(String username) {
        boolean ready=storageReady();
        int devices=ready ? db.queryForObject("""
            SELECT count(*) FROM push_devices WHERE active AND user_id=
                (SELECT id FROM users WHERE user_name=?)
            """,Integer.class,username) : 0;
        return Map.of("storageReady",ready,"pushEnabled",pushEnabled,
                "workersEnabled",workersEnabled,"registeredDevices",devices);
    }
}
