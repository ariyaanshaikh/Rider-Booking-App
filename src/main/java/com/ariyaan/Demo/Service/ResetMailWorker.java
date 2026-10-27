package com.ariyaan.Demo.Service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.background-workers.enabled",havingValue="true",matchIfMissing=true)
public class ResetMailWorker {
    private final JdbcTemplate db;
    private final EmailService email;
    public ResetMailWorker(JdbcTemplate db,EmailService email) { this.db=db;this.email=email; }
    @Scheduled(fixedDelay=5000,initialDelay=5000)
    public void deliver() {
        var jobs=db.queryForList("""
            WITH due AS (SELECT id FROM reset_mail_jobs WHERE available_at<=now() AND expires_at>now() AND attempts<5
                AND (state='PENDING' OR (state='SENDING' AND lease_until<now())) ORDER BY available_at LIMIT 10 FOR UPDATE SKIP LOCKED)
            UPDATE reset_mail_jobs j SET state='SENDING',attempts=attempts+1,lease_until=now()+interval '2 minutes'
            FROM due WHERE j.id=due.id RETURNING j.*
            """);
        for(var job:jobs) {
            if(!Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM password_resets r JOIN reset_mail_jobs j ON j.user_id=r.user_id
                WHERE j.id=? AND j.state='SENDING' AND r.otp=j.otp AND r.verified_at IS NULL AND r.used_at IS NULL AND r.expires_at>now())
                """,Boolean.class,job.get("id")))) {
                db.update("UPDATE reset_mail_jobs SET state='CANCELLED' WHERE id=? AND state='SENDING'",job.get("id"));continue;
            }
            try {
                email.sendReset(job.get("email").toString(),job.get("otp").toString());
                db.update("UPDATE reset_mail_jobs SET state='SENT',last_error=NULL WHERE id=? AND state='SENDING' AND attempts=?",job.get("id"),job.get("attempts"));
            } catch(RuntimeException ex) {
                int attempts=((Number)job.get("attempts")).intValue();
                db.update("UPDATE reset_mail_jobs SET state=?,last_error='SMTP_DELIVERY_FAILED',available_at=now()+make_interval(secs=>?) WHERE id=? AND state='SENDING' AND attempts=?",
                        attempts>=5?"FAILED":"PENDING",Math.min(300,5*(1<<attempts)),job.get("id"),attempts);
            }
        }
        db.update("UPDATE reset_mail_jobs SET state='EXPIRED' WHERE expires_at<now() AND state IN ('PENDING','SENDING')");
    }
}
