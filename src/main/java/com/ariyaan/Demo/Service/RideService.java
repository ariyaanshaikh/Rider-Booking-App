package com.ariyaan.Demo.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ariyaan.Demo.Entity.InvoiceEntity;
import com.ariyaan.Demo.Entity.RideDetailsEntity;
import com.ariyaan.Demo.Entity.RideRequestEntity;
import com.ariyaan.Demo.Entity.RiderDemoEntity;
import com.ariyaan.Demo.Entity.User;
import com.ariyaan.Demo.Repository.InvoiceRepository;
import com.ariyaan.Demo.Repository.RideDetailsRepository;
import com.ariyaan.Demo.Repository.RideRequestRepository;
import com.ariyaan.Demo.Repository.UserRepository;
import com.ariyaan.Demo.exception.InvalidRideStatusException;
import com.ariyaan.Demo.exception.RideNotFoundException;

@Service
public class RideService {

    private final RideDetailsRepository rideDetailsRepository;
    private final RideRequestRepository rideRequestRepository;
    private final RiderMatchingService riderMatchingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.ariyaan.Demo.Repository.RiderDemoRepository riderDemoRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;

    public RideService(RideDetailsRepository rideDetailsRepository,
                       RideRequestRepository rideRequestRepository,
                       RiderMatchingService riderMatchingService,
                       SimpMessagingTemplate messagingTemplate,
                       com.ariyaan.Demo.Repository.RiderDemoRepository riderDemoRepository,
                       InvoiceRepository invoiceRepository,
                       UserRepository userRepository) {
        this.rideDetailsRepository = rideDetailsRepository;
        this.rideRequestRepository = rideRequestRepository;
        this.riderMatchingService = riderMatchingService;
        this.messagingTemplate = messagingTemplate;
        this.riderDemoRepository = riderDemoRepository;
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
    }

    /**
     * Attempt to accept the ride atomically. If success, update RideRequest rows
     * (winner ACCEPTED, others CANCELLED) and notify user and other riders.
     */
    @Transactional
    public boolean acceptRide(int rideId, int requestedRiderId, String username) {
        User account = userRepository.findByUserName(username)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user was not found"));
        RiderDemoEntity authenticatedRider = riderDemoRepository.findByUserId(account.getId())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user is not linked to a rider profile"));
        if (authenticatedRider.getId() != requestedRiderId) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "A rider can only accept rides for their own profile");
        }

        // Serializes concurrent accept attempts for this rider.
        RiderDemoEntity rider = riderDemoRepository.findByIdForUpdate(authenticatedRider.getId())
                .orElseThrow(() -> new IllegalArgumentException("Rider profile was not found"));
        int riderId = (int) rider.getId();
        if (!"AVAILABLE".equals(rider.getStatus())
                || rideDetailsRepository.existsByRiderIdAndRideStatusIn(riderId, List.of("ACCEPTED", "STARTED"))
                || !rideRequestRepository.existsByRideIdAndRiderIdAndStatus(rideId, riderId, "SENT")) {
            return false;
        }

        int updated = rideDetailsRepository.acceptRide(rideId, riderId);
        if (updated != 1) {
            return false;
        }

        // An accepted rider must immediately be excluded from further matching.
        rider.setStatus("ON_TRIP");
        riderDemoRepository.save(rider);

        RideDetailsEntity ride = rideDetailsRepository.findById(rideId).orElseThrow();
        int userId = ride.getAdminId();

        // Update RideRequest rows
        List<RideRequestEntity> requests = rideRequestRepository.findByRideId(rideId);
        for (RideRequestEntity req : requests) {
            if (req.getRiderId() == riderId) {
                req.setStatus("ACCEPTED");
            } else {
                req.setStatus("CANCELLED");
            }
        }
        rideRequestRepository.saveAll(requests);

        // Notify user
        messagingTemplate.convertAndSend(
                "/topic/user/" + userId,
                (Object) Map.of(
                        "type", "RIDE_ACCEPTED",
                        "rideId", rideId,
                        "riderId", riderId,
                        "otp", ride.getRideOtp()
                )
        );

        // Notify cancelled riders
        for (RideRequestEntity req : requests) {
            if (req.getRiderId() != riderId) {
                messagingTemplate.convertAndSend(
                        "/topic/rider/" + req.getRiderId(),
                        (Object) Map.of(
                                "type", "RIDE_CANCELLED",
                                "rideId", rideId
                        )
                );
            }
        }

        return true;
    }

    /**
     * Handle rider cancellation. Notify user, mark ride as RIDER_CANCELLED,
     * then attempt to rematch next nearest riders (excluding already-notified ones).
     */
    @Transactional
    public void riderCancel(int rideId, int riderId) {
        RideDetailsEntity ride = rideDetailsRepository.findById(rideId).orElseThrow();

        // Only proceed if this rider was the assigned rider
        if (ride.getRiderId() != riderId) {
            // ignore or throw
            return;
        }

        // Mark ride as cancelled by rider
        ride.setRideStatus("RIDER_CANCELLED");
        ride.setRiderId(0);
        rideDetailsRepository.save(ride);

        riderDemoRepository.findById((long) riderId).ifPresent(rider -> {
            rider.setStatus("AVAILABLE");
            riderDemoRepository.save(rider);
        });

        // Notify user
        messagingTemplate.convertAndSend(
                "/topic/user/" + ride.getAdminId(),
                (Object) Map.of(
                        "type", "RIDER_CANCELLED",
                        "rideId", rideId,
                        "riderId", riderId
                )
        );

        // Find next nearest riders excluding those who already had requests for this ride
        List<RideRequestEntity> existing = rideRequestRepository.findByRideId(rideId);
        List<Integer> excluded = new ArrayList<>();
        for (RideRequestEntity r : existing) excluded.add(r.getRiderId());

        List<Integer> candidates = riderMatchingService.findNearestRiders(ride.getPickUpLatitude(), ride.getPickUpLongitude());
        List<RideRequestEntity> newRequests = new ArrayList<>();
        Timestamp now = Timestamp.from(Instant.now());
        for (int cand : candidates) {
            if (excluded.contains(cand)) continue;
            RideRequestEntity req = new RideRequestEntity();
            req.setRideId(rideId);
            req.setRiderId(cand);
            req.setStatus("SENT");
            req.setCreatedAt(now);
            newRequests.add(req);
        }
        if (!newRequests.isEmpty()) {
            rideRequestRepository.saveAll(newRequests);
        }

        // Notify the new riders
        for (RideRequestEntity req : newRequests) {
            messagingTemplate.convertAndSend(
                    "/topic/rider/" + req.getRiderId(),
                    (Object) Map.of(
                            "type", "RIDE_REQUEST",
                            "rideId", rideId,
                            "pickupLat", ride.getPickUpLatitude(),
                            "pickupLon", ride.getPickUpLongitude(),
                            "dropLat", ride.getDropLatitude(),
                            "dropLon", ride.getDropLongitude(),
                            "fare", ride.getFare()
                    )
            );
        }

        // Optionally set ride status back to REQUESTED for further matching
        ride.setRideStatus("REQUESTED");
        rideDetailsRepository.save(ride);
    }

    /**
     * Handle user/admin cancellation. Mark ride ADMIN_CANCELLED and notify assigned rider (if any).
     */
    @Transactional
    public void userCancel(int rideId) {
        RideDetailsEntity ride = rideDetailsRepository.findById(rideId).orElseThrow();
        ride.setRideStatus("ADMIN_CANCELLED");
        rideDetailsRepository.save(ride);

        int riderId = ride.getRiderId();
        if (riderId > 0) {
			riderDemoRepository.findById((long) riderId).ifPresent(rider -> {
				rider.setStatus("AVAILABLE");
				riderDemoRepository.save(rider);
			});
            // notify rider
            messagingTemplate.convertAndSend(
                    "/topic/rider/" + riderId,
                    (Object) Map.of(
                            "type", "RIDE_CANCELLED_BY_USER",
                            "rideId", rideId
                    )
            );
        }

        // Update ride request statuses
        List<RideRequestEntity> requests = rideRequestRepository.findByRideId(rideId);
        for (RideRequestEntity req : requests) {
            req.setStatus("CANCELLED");
        }
        rideRequestRepository.saveAll(requests);
    }

    @Transactional
    public boolean startRide(int rideId, int riderId, int otp) {
        RideDetailsEntity ride = rideDetailsRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + rideId));

        if (ride.getRiderId() != riderId) {
            throw new InvalidRideStatusException("Rider is not assigned to this ride");
        }
        if (!"ACCEPTED".equals(ride.getRideStatus())) {
            throw new InvalidRideStatusException("Ride status must be ACCEPTED to start");
        }

        if (ride.getRideOtp() != otp) {
            throw new InvalidRideStatusException("OTP is incorrect");
        }

        ride.setRideStatus("STARTED");
        rideDetailsRepository.save(ride);

        // mark rider ON_TRIP
        riderDemoRepository.findById((long) riderId).ifPresent(r -> {
            r.setStatus("ON_TRIP");
            riderDemoRepository.save(r);
        });

        // notify both user and rider
        messagingTemplate.convertAndSend(
                "/topic/ride/" + rideId,
                (Object) Map.of(
                        "type", "RIDE_STARTED",
                        "rideId", rideId
                )
        );

        messagingTemplate.convertAndSend(
                "/topic/user/" + ride.getAdminId(),
                (Object) Map.of(
                        "type", "RIDE_STARTED",
                        "rideId", rideId
                )
        );

        return true;
    }

    /**
     * End ride: mark as COMPLETED, make rider AVAILABLE, and create an invoice.
     * Invoice is marked as PENDING for settlement (payment processing can be done async).
     */
    @Transactional
    public boolean endRide(int rideId, int riderId) {
        RideDetailsEntity ride = rideDetailsRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + rideId));
        
        if (ride.getRiderId() != riderId) {
            throw new InvalidRideStatusException("Rider is not assigned to this ride");
        }
        if (!"STARTED".equals(ride.getRideStatus())) {
            throw new InvalidRideStatusException("Ride status must be STARTED to end");
        }

        // Mark ride as completed
        ride.setRideStatus("COMPLETED");
        rideDetailsRepository.save(ride);

        // Mark rider AVAILABLE
        riderDemoRepository.findById((long) riderId).ifPresent(r -> {
            r.setStatus("AVAILABLE");
            riderDemoRepository.save(r);
        });

        // Create invoice record
        // Amount = fare (in production, you might apply commission, discounts, taxes, etc.)
        InvoiceEntity invoice = new InvoiceEntity(
                rideId,
                riderId,
                ride.getAdminId(),  // userId
                ride.getFare(),     // amount
                LocalDateTime.now(),
                "PENDING"           // status: awaiting payment/settlement
        );
        invoiceRepository.save(invoice);

        // Notify both user and ride topic
        messagingTemplate.convertAndSend(
                "/topic/ride/" + rideId,
                (Object) Map.of(
                        "type", "RIDE_COMPLETED",
                        "rideId", rideId,
                        "fare", ride.getFare(),
                        "invoiceId", invoice.getInvoiceId()
                )
        );

        messagingTemplate.convertAndSend(
                "/topic/user/" + ride.getAdminId(),
                (Object) Map.of(
                        "type", "RIDE_COMPLETED",
                        "rideId", rideId,
                        "fare", ride.getFare(),
                        "invoiceId", invoice.getInvoiceId()
                )
        );

        return true;
    }

	/*
	  public void paymentCompleted(int rideId, String paymentCompleted, String
	  paymentJson) { rideDetailsRepository.save(null); }
	 */
    
    
    @Transactional
    public void paymentCompleted(int rideId, String paymentCompleted, String paymentJson) {
        int updatedRows = rideDetailsRepository.updatePaymentStatusAndJson(rideId, paymentCompleted, paymentJson);
        
        if (updatedRows == 0) {
            throw new RideNotFoundException("Ride not found with id: " + rideId);
        }

        // Optional: Notify frontend clients via WebSocket that payment has been recorded
        messagingTemplate.convertAndSend(
                "/topic/ride/" + rideId,
                (Object) Map.of(
                        "type", "PAYMENT_COMPLETED",
                        "rideId", rideId,
                        "paymentCompleted", paymentCompleted
                )
        );
    }
    
    

}
