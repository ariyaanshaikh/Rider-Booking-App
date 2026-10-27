package com.ariyaan.Demo.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

/** Only unassigned, unpaid requests expire. Payment reconciliation is never guessed. */
@Service
public class DispatchExpiry {
    private final JdbcTemplate db;
    private final SimpMessagingTemplate messages;
    private final int minutes;
    public DispatchExpiry(JdbcTemplate db, SimpMessagingTemplate messages, @Value("${app.dispatch-expiry-minutes:15}") int minutes) {
        this.db=db; this.messages=messages; this.minutes=Math.max(1,minutes);
    }
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    @Transactional
    public void expire() {
        var rows=db.queryForList("SELECT ride_id,admin_id FROM ride_details WHERE ride_status='REQUESTED' AND created_at < now()-make_interval(mins=>?) ORDER BY ride_id LIMIT 100 FOR UPDATE SKIP LOCKED",minutes);
        for (var row:rows) {
            int id=((Number)row.get("ride_id")).intValue();
            db.update("UPDATE ride_details SET ride_status='ADMIN_CANCELLED' WHERE ride_id=?",id);
            var payload=Map.of("type","RIDE_CANCELLED","rideId",id,"reason","DISPATCH_TIMEOUT");
            for (var rider:db.queryForList("SELECT rider_id FROM ride_request WHERE ride_id=? AND status='SENT'",id))
                messages.convertAndSend("/topic/rider/"+rider.get("rider_id"),payload);
            db.update("UPDATE ride_request SET status='CANCELLED' WHERE ride_id=?",id);
            messages.convertAndSend("/topic/user/"+row.get("admin_id"),payload);
        }
    }
}
