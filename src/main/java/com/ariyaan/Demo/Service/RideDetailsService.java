package com.ariyaan.Demo.Service;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import com.ariyaan.Demo.Dto.RideDetailsDTO;
import com.ariyaan.Demo.Entity.RideDetailsEntity;
import com.ariyaan.Demo.Entity.RideRequestEntity;
import com.ariyaan.Demo.Repository.RideDetailsRepository;
import com.ariyaan.Demo.Repository.RideRequestRepository;

@Service
public class RideDetailsService {

    private final RideDetailsRepository rideDetailsRepository;
    private final RiderMatchingService riderMatchingService;
    private final RideRequestRepository rideRequestRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final DistanceService distanceService;
    private final com.ariyaan.Demo.Repository.UserRepository users;

    private final SecureRandom secureRandom = new SecureRandom();
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate db;

    public RideDetailsService(RideDetailsRepository rideDetailsRepository,
                              RiderMatchingService riderMatchingService,
                              RideRequestRepository rideRequestRepository,
                              SimpMessagingTemplate messagingTemplate, DistanceService distanceService,
                              com.ariyaan.Demo.Repository.UserRepository users) {
        this.rideDetailsRepository = rideDetailsRepository;
        this.riderMatchingService = riderMatchingService;
        this.rideRequestRepository = rideRequestRepository;
        this.messagingTemplate = messagingTemplate;
        this.distanceService = distanceService;
        this.users = users;
    }

    /**
     * Creates a RideDetails entry, generates OTP, finds nearest riders,
     * creates RideRequest rows and notifies riders over WebSocket.
     * Returns the generated ride id.
     */
    @Transactional
    public int saveRideDetials(RideDetailsDTO rideRequest, String username, String bookingKey) {
        var account = users.findByUserName(username).orElseThrow();
        if (bookingKey != null) {
            if (!bookingKey.matches("[A-Za-z0-9_-]{8,100}")) throw new IllegalArgumentException("Invalid Idempotency-Key");
            db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", account.getId()+":"+bookingKey);
            var previous=rideDetailsRepository.findByAdminIdAndBookingKey(Math.toIntExact(account.getId()),bookingKey);
            if (previous.isPresent()) {
                var ride=previous.get();
                if (ride.getPickUpLatitude()!=rideRequest.getPickUpLatitude() || ride.getPickUpLongitude()!=rideRequest.getPickUpLongitude()
                        || ride.getDropLatitude()!=rideRequest.getDropLatitude() || ride.getDropLongitude()!=rideRequest.getDropLongitude()
                        || !java.util.Objects.equals(ride.getChassisNo(),rideRequest.getChassisNo())
                        || !java.util.Objects.equals(ride.getVehicleName(),rideRequest.getVehicleName()))
                    throw new IllegalStateException("Idempotency-Key already belongs to a different booking");
                return ride.getRideId();
            }
        }
        RideDetailsEntity rideDetailsEntity = new RideDetailsEntity();
        rideDetailsEntity.setBookingKey(bookingKey);
        rideDetailsEntity.setPickUpLatitude(rideRequest.getPickUpLatitude());
        rideDetailsEntity.setPickUpLongitude(rideRequest.getPickUpLongitude());
        rideDetailsEntity.setDropLatitude(rideRequest.getDropLatitude());
        rideDetailsEntity.setDropLongitude(rideRequest.getDropLongitude());
        rideDetailsEntity.setChassisNo(rideRequest.getChassisNo());
        rideDetailsEntity.setVehicleName(rideRequest.getVehicleName());
        rideDetailsEntity.setRideId(0);
        rideDetailsEntity.setAdminId(Math.toIntExact(account.getId()));
        rideDetailsEntity.setRiderId(0);
        long meters = distanceService.getDistanceMeters(rideRequest.getPickUpLatitude(), rideRequest.getPickUpLongitude(),
                rideRequest.getDropLatitude(), rideRequest.getDropLongitude());
        rideDetailsEntity.setDistanceMeters(meters);
        rideDetailsEntity.setFare(java.math.BigDecimal.valueOf(FarePolicy.quote(meters).amountPaise(), 2).doubleValue());
        rideDetailsEntity.setPaymentCompleted("PENDING");

        int otp = 1000 + secureRandom.nextInt(9000);
        rideDetailsEntity.setRideOtp(otp);
        rideDetailsEntity.setRideStatus("REQUESTED");
        // The ride-start OTP remains valid until the ride is started or cancelled.

        RideDetailsEntity saved = rideDetailsRepository.save(rideDetailsEntity);
        int rideId = saved.getRideId();

        // find nearest riders (this uses RiderMatchingRepository via service)
        List<Integer> riders = riderMatchingService.findNearestRiders(
                saved.getPickUpLatitude(), saved.getPickUpLongitude());

        // create RideRequest rows
        List<RideRequestEntity> requests = new ArrayList<>();
        Timestamp now = Timestamp.from(Instant.now());
        for (int riderId : riders) {
            RideRequestEntity req = new RideRequestEntity();
            req.setRideId(rideId);
            req.setRiderId(riderId);
            req.setStatus("SENT");
            req.setCreatedAt(now);
            requests.add(req);
        }

        if (!requests.isEmpty()) {
            rideRequestRepository.saveAll(requests);
        }

        // Notify riders via WebSocket with a concise payload
        for (int riderId : riders) {
            messagingTemplate.convertAndSend(
                    "/topic/rider/" + riderId,
                    (Object) Map.of(
                            "type", "RIDE_REQUEST",
                            "rideId", rideId,
                            "pickupLat", saved.getPickUpLatitude(),
                            "pickupLon", saved.getPickUpLongitude(),
                            "dropLat", saved.getDropLatitude(),
                            "dropLon", saved.getDropLongitude(),
                            "fare", saved.getFare(),
                            "chassisNo", saved.getChassisNo(),
                            "vehicleName", saved.getVehicleName()
                    )
            );
        }

        return rideId;
    }

    public boolean acceptRide(int rideId, int riderId) {
        int updated = rideDetailsRepository.acceptRide(rideId, riderId);
        return updated == 1;
    }

}
