package com.ariyaan.Demo.Controller;

import com.ariyaan.Demo.Repository.UserRepository;
import com.ariyaan.Demo.Service.EventNotifications;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final JdbcTemplate db;
    private final UserRepository users;
    private final EventNotifications events;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ariyaan.Demo.Service.RequestLimits limits;
    @org.springframework.beans.factory.annotation.Value("${app.dispatch-expiry-minutes:15}")
    private int dispatchMinutes=15;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ariyaan.Demo.Service.RideService rides;
    public NotificationController(JdbcTemplate db,UserRepository users,EventNotifications events) { this.db=db;this.users=users;this.events=events; }
    public record Device(@Pattern(regexp="admin|rider") @NotNull String app,
            @Pattern(regexp="[A-Za-z0-9_-]{16,64}") @NotNull String installationId,
            @Pattern(regexp="(ExponentPushToken|ExpoPushToken)\\[[A-Za-z0-9_-]{8,150}\\]") @NotNull String token,
            @Pattern(regexp="android|ios") @NotNull String platform) {}
    @PutMapping("/devices") @Transactional
    public Map<String,Boolean> register(Authentication auth,@Valid @RequestBody Device device) {
        var user=users.findByUserName(auth.getName()).orElseThrow();
        limits.check("push-device",auth.getName(),60);
        if (!user.getRole().name().equalsIgnoreCase(device.app())) throw new org.springframework.security.access.AccessDeniedException("App does not match account role");
        // Serializes registration/account switches for the same device and token across app instances.
        db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",device.token());
        db.update("DELETE FROM push_devices WHERE token=? AND (app<>? OR installation_id<>?)",device.token(),device.app(),device.installationId());
        db.update("""
            INSERT INTO push_devices(app,installation_id,user_id,token,platform) VALUES(?,?,?,?,?)
            ON CONFLICT(app,installation_id) DO UPDATE SET user_id=EXCLUDED.user_id,token=EXCLUDED.token,
            platform=EXCLUDED.platform,active=true,updated_at=now()
            """,device.app(),device.installationId(),user.getId(),device.token(),device.platform());
        events.replayRecent(user.getId());
        return Map.of("registered",true);
    }
    @DeleteMapping("/devices/{app}/{installationId}")
    public Map<String,Boolean> unregister(Authentication auth,@PathVariable String app,@PathVariable String installationId) {
        db.update("UPDATE push_devices SET active=false WHERE app=? AND installation_id=? AND user_id=(SELECT id FROM users WHERE user_name=?)",app,installationId,auth.getName());
        return Map.of("unregistered",true);
    }
    @GetMapping
    public List<Map<String,Object>> inbox(Authentication auth) {
        return db.queryForList("SELECT id AS \"eventId\",type,ride_id AS \"rideId\",created_at AS \"createdAt\" FROM notification_inbox WHERE user_id=(SELECT id FROM users WHERE user_name=?) ORDER BY created_at DESC LIMIT 50",auth.getName());
    }
    @GetMapping("/ride-requests")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RIDER')")
    public List<Map<String,Object>> pendingRequests(Authentication auth) {
        return pendingRequests(auth.getName(),0);
    }
    @GetMapping("/ride-requests/{rideId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RIDER')")
    public Map<String,Object> pendingRequest(Authentication auth,@PathVariable int rideId) {
        if(rideId<1) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Offer not available");
        return pendingRequests(auth.getName(),rideId).stream().findFirst().orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Offer not available"));
    }
    @PostMapping("/ride-requests/{rideId}/decline")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('RIDER')")
    public Map<String,Object> decline(Authentication auth,@PathVariable int rideId) {
        rides.declineOffer(rideId,auth.getName());
        return Map.of("declined",true,"rideId",rideId);
    }
    private List<Map<String,Object>> pendingRequests(String username,int rideId) {
        return db.queryForList("""
            SELECT r.ride_id AS "rideId",r.pickup_latitude AS "pickupLatitude",r.pickup_longitude AS "pickupLongitude",
                r.drop_latitude AS "dropLatitude",r.drop_longitude AS "dropLongitude",r.fare,r.vehicle_name AS "vehicleName",r.chassis_no AS "chassisNo"
            FROM ride_request q JOIN ride_details r ON r.ride_id=q.ride_id JOIN rider_details d ON d.id=q.rider_id
            JOIN users u ON u.id=d.user_id WHERE u.user_name=? AND q.status='SENT' AND r.ride_status='REQUESTED'
            AND r.created_at>now()-make_interval(mins=>?) AND d.status='AVAILABLE'
            AND q.created_at>now()-make_interval(mins=>?) AND (?=0 OR r.ride_id=?)
            ORDER BY q.created_at DESC LIMIT 10
            """,username,Math.max(1,dispatchMinutes),Math.max(1,dispatchMinutes),rideId,rideId);
    }
    @GetMapping("/delivery-status")
    @org.springframework.security.access.prepost.PreAuthorize("@riderAccess.operator(authentication)")
    public Map<String,Object> status() {
        return Map.of("push",db.queryForList("SELECT state,count(*) AS count FROM push_jobs GROUP BY state"),
                "passwordResetMail",db.queryForList("SELECT state,count(*) AS count FROM reset_mail_jobs GROUP BY state"));
    }
}
