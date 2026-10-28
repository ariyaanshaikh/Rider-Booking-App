package com.ariyaan.Demo.Service;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.background-workers.enabled",havingValue="true",matchIfMissing=true)
public class PushWorker {
    private final JdbcTemplate db;
    private final ExpoPushClient expo;
    private final boolean enabled;
    @Value("${app.dispatch-expiry-minutes:15}")
    private int dispatchMinutes=15;
    public PushWorker(JdbcTemplate db,ExpoPushClient expo,@Value("${app.push.enabled:false}") boolean enabled) {
        this.db=db;this.expo=expo;this.enabled=enabled;
    }
    @Scheduled(fixedDelay=5000,initialDelay=5000)
    public void deliver() {
        if(!enabled) return;
        db.update("""
            UPDATE push_jobs j SET state='CANCELLED' WHERE state IN ('PENDING','SENDING') AND
            (expires_at<=now() OR NOT EXISTS(SELECT 1 FROM push_devices d JOIN notification_inbox n ON n.user_id=d.user_id
              JOIN users u ON u.id=d.user_id AND u.enabled WHERE n.id=j.notification_id AND d.app=j.app
              AND d.installation_id=j.installation_id AND d.active AND d.token=j.token))
            """);
        db.update("UPDATE push_jobs SET state='FAILED',last_error='RETRY_EXHAUSTED' WHERE state='SENDING' AND lease_until<now() AND attempts>=8");
        var jobs=db.queryForList("""
            WITH due AS (SELECT id FROM push_jobs WHERE available_at<=now() AND expires_at>now() AND attempts<8
                AND (state='PENDING' OR (state='SENDING' AND lease_until<now())) ORDER BY available_at LIMIT 5 FOR UPDATE SKIP LOCKED)
            UPDATE push_jobs j SET state='SENDING',attempts=attempts+1,lease_until=now()+interval '2 minutes'
            FROM due WHERE j.id=due.id RETURNING j.*,extract(epoch FROM j.expires_at-now())::int AS ttl
            """);
        for(var job:jobs) {
            var notifications=db.queryForList("SELECT n.* FROM notification_inbox n JOIN push_devices d ON n.user_id=d.user_id WHERE n.id=? AND d.app=? AND d.installation_id=? AND d.active AND d.token=?",
                    job.get("notification_id"),job.get("app"),job.get("installation_id"),job.get("token"));
            if(notifications.isEmpty()) { finish(job,"CANCELLED",null);continue; }
            var notification=notifications.get(0);
            if("RIDE_REQUEST".equals(notification.get("type")) && !Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM ride_details r JOIN ride_request q ON q.ride_id=r.ride_id
                JOIN rider_details d ON d.id=q.rider_id WHERE r.ride_id=? AND r.ride_status='REQUESTED'
                AND q.status='SENT' AND d.user_id=? AND d.status='AVAILABLE'
                AND r.created_at>now()-make_interval(mins=>?)
                AND ?='RIDE_REQUEST:'||q.id||':'||(extract(epoch FROM q.created_at)*1000000)::bigint::text)
                """,Boolean.class,notification.get("ride_id"),notification.get("user_id"),Math.max(1,dispatchMinutes),notification.get("event_key")))) { finish(job,"CANCELLED",null);continue; }
            try {
                var result=expo.send(job.get("token").toString(),Map.of("eventId",notification.get("id").toString(),
                        "type",notification.get("type"),"rideId",notification.get("ride_id"),"remote",true),((Number)job.get("ttl")).intValue());
                if("ok".equals(result.status()) && !result.id().isBlank()) {
                    db.update("UPDATE push_jobs SET state='TICKET',receipt_id=?,available_at=now()+interval '15 minutes',lease_until=NULL WHERE id=? AND state='SENDING' AND attempts=?",
                            result.id(),job.get("id"),job.get("attempts"));
                } else fail(job,result.error());
            } catch(RuntimeException ex) { retry(job,"PROVIDER_UNAVAILABLE"); }
        }
    }
    private void fail(Map<String,Object> job,String error) {
        if("DeviceNotRegistered".equals(error)) {
            db.update("UPDATE push_devices SET active=false WHERE app=? AND installation_id=? AND token=?",job.get("app"),job.get("installation_id"),job.get("token"));
            finish(job,"FAILED","DeviceNotRegistered");
        } else if(Set.of("MessageTooBig","InvalidCredentials","MismatchSenderId").contains(error)) finish(job,"FAILED",error);
        else retry(job,"MessageRateExceeded".equals(error)?error:"PROVIDER_ERROR");
    }
    private void finish(Map<String,Object> job,String state,String error) {
        db.update("UPDATE push_jobs SET state=?,last_error=?,lease_until=NULL WHERE id=? AND attempts=? AND state IN ('SENDING','CHECKING')",state,error,job.get("id"),job.get("attempts"));
    }
    private void retry(Map<String,Object> job,String error) {
        int attempts=((Number)job.get("attempts")).intValue();
        db.update("UPDATE push_jobs SET state=?,last_error=?,available_at=now()+make_interval(secs=>?),lease_until=NULL WHERE id=? AND attempts=? AND state IN ('SENDING','CHECKING')",
                attempts>=8?"FAILED":"PENDING",error,Math.min(900,10*(1<<Math.min(attempts,6))),job.get("id"),attempts);
    }
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void receipts() {
        if(!enabled)return;
        var jobs=db.queryForList("""
            WITH due AS (SELECT id FROM push_jobs WHERE created_at>now()-interval '24 hours' AND available_at<=now()
                AND (state='TICKET' OR (state='CHECKING' AND lease_until<now())) ORDER BY available_at LIMIT 5 FOR UPDATE SKIP LOCKED)
            UPDATE push_jobs j SET state='CHECKING',lease_until=now()+interval '2 minutes'
            FROM due WHERE j.id=due.id RETURNING j.*
            """);
        for(var job:jobs) {
            try {
                var result=expo.receipt(job.get("receipt_id").toString());
                if("ok".equals(result.status())) finish(job,"DELIVERED",null);
                else if("pending".equals(result.status())) waitReceipt(job);
                else fail(job,result.error());
            } catch(RuntimeException ex) { waitReceipt(job); }
        }
        db.update("UPDATE push_jobs SET state='FAILED',last_error='RECEIPT_TIMEOUT' WHERE state IN ('TICKET','CHECKING') AND created_at<now()-interval '24 hours'");
    }
    private void waitReceipt(Map<String,Object> job) {
        db.update("UPDATE push_jobs SET state='TICKET',available_at=now()+interval '15 minutes',lease_until=NULL WHERE id=? AND state='CHECKING'",job.get("id"));
    }
    @Scheduled(fixedDelay=3600000,initialDelay=3600000)
    public void prune() {
        db.update("DELETE FROM notification_inbox WHERE created_at<now()-interval '30 days'");
        db.update("DELETE FROM reset_mail_jobs WHERE expires_at<now()-interval '1 day'");
        db.update("UPDATE reset_mail_jobs SET state='FAILED',last_error='RETRY_EXHAUSTED' WHERE state='SENDING' AND lease_until<now() AND attempts>=5");
    }
}
