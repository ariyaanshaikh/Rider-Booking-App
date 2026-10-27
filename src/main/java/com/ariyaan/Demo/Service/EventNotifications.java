package com.ariyaan.Demo.Service;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Minimal private inbox and durable push intents; never store OTPs, GPS, KYC or bank details in push payloads. */
@Service
public class EventNotifications {
    private final JdbcTemplate db;
    public EventNotifications(JdbcTemplate db) { this.db=db; }
    private static final Set<String> TYPES=Set.of("RIDE_REQUEST","RIDE_ACCEPTED","RIDER_CANCELLED","RIDE_CANCELLED",
            "RIDE_CANCELLED_BY_USER","PAYMENT_REQUIRED","RIDE_STARTED","RIDE_COMPLETED");
    @Transactional
    public UUID enqueue(String destination,Object payload) {
        if (!(payload instanceof Map<?,?> data) || !TYPES.contains(String.valueOf(data.get("type")))
                || !(data.get("rideId") instanceof Number ride)) return null;
        List<Long> owners;
        try {
            if (destination.matches("/topic/user/[1-9][0-9]*"))
                owners=db.queryForList("SELECT id FROM users WHERE id=? AND enabled",Long.class,Long.parseLong(destination.substring(12)));
            else if (destination.matches("/topic/rider/[1-9][0-9]*"))
                owners=db.queryForList("SELECT u.id FROM users u JOIN rider_details r ON r.user_id=u.id WHERE r.id=? AND u.enabled",Long.class,Long.parseLong(destination.substring(13)));
            else return null;
        } catch (NumberFormatException ex) { return null; }
        if (owners.isEmpty()) return null;
        String type=data.get("type").toString();
        String key=(Set.of("RIDE_STARTED","RIDE_COMPLETED","RIDE_CANCELLED_BY_USER").contains(type) ? type+":"+ride : UUID.randomUUID().toString());
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO notification_inbox(id,user_id,event_key,type,ride_id) VALUES(?,?,?,?,?) ON CONFLICT(user_id,event_key) DO NOTHING",
                id,owners.get(0),key,type,ride.intValue());
        UUID stored=db.queryForObject("SELECT id FROM notification_inbox WHERE user_id=? AND event_key=?",UUID.class,owners.get(0),key);
        queueDevices(stored);
        return stored;
    }
    public void queueDevices(UUID notificationId) {
        // Token snapshot and owner are checked again at dispatch, so logout/account switches cannot leak another user's rides.
        db.update("""
            INSERT INTO push_jobs(id,notification_id,app,installation_id,token,expires_at)
            SELECT gen_random_uuid(),n.id,d.app,d.installation_id,d.token,
                n.created_at+CASE WHEN n.type='RIDE_REQUEST' THEN interval '5 minutes' ELSE interval '1 hour' END
            FROM notification_inbox n JOIN push_devices d ON d.user_id=n.user_id AND d.active
            WHERE n.id=? ON CONFLICT(notification_id,app,installation_id,token) DO NOTHING
            """,notificationId);
    }
    public void replayRecent(long userId) {
        for(UUID id:db.queryForList("SELECT id FROM notification_inbox WHERE user_id=? AND created_at>now()-interval '5 minutes'",UUID.class,userId)) queueDevices(id);
    }
}
