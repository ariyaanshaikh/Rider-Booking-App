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

    private final SecureRandom secureRandom = new SecureRandom();

    public RideDetailsService(RideDetailsRepository rideDetailsRepository,
                              RiderMatchingService riderMatchingService,
                              RideRequestRepository rideRequestRepository,
                              SimpMessagingTemplate messagingTemplate) {
        this.rideDetailsRepository = rideDetailsRepository;
        this.riderMatchingService = riderMatchingService;
        this.rideRequestRepository = rideRequestRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Creates a RideDetails entry, generates OTP, finds nearest riders,
     * creates RideRequest rows and notifies riders over WebSocket.
     * Returns the generated ride id.
     */
    @Transactional
    public int saveRideDetials(RideDetailsDTO rideRequest) {
        RideDetailsEntity rideDetailsEntity = new RideDetailsEntity();
        BeanUtils.copyProperties(rideRequest, rideDetailsEntity);

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
