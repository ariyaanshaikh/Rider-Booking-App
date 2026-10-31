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
    private final RidePaymentStore paymentStore;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ariyaan.Demo.Repository.RiderVerificationDetailsRepository verifications;
    @org.springframework.beans.factory.annotation.Value("${app.dispatch-expiry-minutes:15}")
    private int dispatchMinutes=15;

    public RideService(RideDetailsRepository rideDetailsRepository,
                       RideRequestRepository rideRequestRepository,
                       RiderMatchingService riderMatchingService,
                       SimpMessagingTemplate messagingTemplate,
                       com.ariyaan.Demo.Repository.RiderDemoRepository riderDemoRepository,
                       InvoiceRepository invoiceRepository,
                       UserRepository userRepository, RidePaymentStore paymentStore) {
        this.rideDetailsRepository = rideDetailsRepository;
        this.rideRequestRepository = rideRequestRepository;
        this.riderMatchingService = riderMatchingService;
        this.messagingTemplate = messagingTemplate;
        this.riderDemoRepository = riderDemoRepository;
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
        this.paymentStore = paymentStore;
    }

    /**
     * Attempt to accept the ride atomically. If success, update RideRequest rows
     * (winner ACCEPTED, others CANCELLED) and notify user and other riders.
     */
    @Transactional
    public boolean acceptRide(int rideId, int requestedRiderId, String username) {
        // Every transition locks ride before rider to avoid accept/cancel deadlocks.
        RideDetailsEntity ride = rideDetailsRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found"));
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
        if (!verifications.findById(rider.getId()).map(v -> Boolean.TRUE.equals(v.getVerified())).orElse(false)) return false;
        if (ride.getCreatedAt()!=null && ride.getCreatedAt().isBefore(Instant.now().minusSeconds(Math.max(1,dispatchMinutes)*60L))) return false;
        if (!"AVAILABLE".equals(rider.getStatus())
                || rideDetailsRepository.existsByRiderIdAndRideStatusIn(riderId, List.of("ACCEPTED", "AWAITING_PAYMENT", "STARTED"))
                || !rideRequestRepository.existsByRideIdAndRiderIdAndStatusAndCreatedAtAfter(rideId, riderId, "SENT",
                    Timestamp.from(Instant.now().minusSeconds(Math.max(1,dispatchMinutes)*60L)))) {
            return false;
        }

        if (!"REQUESTED".equals(ride.getRideStatus()) || ride.getRiderId() != 0) {
            return false;
        }
        ride.setRiderId(riderId);
        ride.setRideStatus("ACCEPTED");
        rideDetailsRepository.save(ride);

        // An accepted rider must immediately be excluded from further matching.
        rider.setStatus("ON_TRIP");
        riderDemoRepository.save(rider);

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
    public void riderCancel(int rideId, int riderId, String username) {
        requireRider(username, riderId);
        RideDetailsEntity ride = rideDetailsRepository.findByIdForUpdate(rideId).orElseThrow();
        if (!"ACCEPTED".equals(ride.getRideStatus()))
            throw new IllegalStateException("Rider cancellation is allowed before OTP verification only; contact support after payment begins");

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

        // Reuse prior offers without inserting duplicate ride/rider rows; exclude the cancelling rider.
        List<RideRequestEntity> existing = rideRequestRepository.findByRideId(rideId);
        List<Integer> excluded = new ArrayList<>();
        for (RideRequestEntity r : existing) r.setStatus("CANCELLED");
        rideRequestRepository.saveAll(existing);
        excluded.add(riderId);

        List<Integer> candidates = riderMatchingService.findNearestRiders(ride.getPickUpLatitude(), ride.getPickUpLongitude());
        List<RideRequestEntity> newRequests = new ArrayList<>();
        Timestamp now = Timestamp.from(Instant.now());
        for (int cand : candidates) {
            if (excluded.contains(cand)) continue;
            RideRequestEntity req = existing.stream().filter(r -> r.getRiderId()==cand).findFirst().orElseGet(RideRequestEntity::new);
            req.setRideId(rideId);
            req.setRiderId(cand);
            req.setStatus("SENT");
            req.setCreatedAt(now);
            newRequests.add(req);
        }
        if (!newRequests.isEmpty()) {
            rideRequestRepository.saveAllAndFlush(newRequests);
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
    public void userCancel(int rideId, String username) {
        RideDetailsEntity ride = rideDetailsRepository.findByIdForUpdate(rideId).orElseThrow();
        var account = userRepository.findByUserName(username).orElseThrow();
        if (account.getId() != ride.getAdminId())
            throw new org.springframework.security.access.AccessDeniedException("Only the booking admin can cancel this ride");
        if (!List.of("REQUESTED", "ACCEPTED").contains(ride.getRideStatus()))
            throw new IllegalStateException("Cancellation after OTP verification requires support and payment reconciliation");
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
            if ("SENT".equals(req.getStatus()) && req.getRiderId()!=riderId) {
                messagingTemplate.convertAndSend("/topic/rider/"+req.getRiderId(),
                        (Object)Map.of("type","RIDE_CANCELLED_BY_USER","rideId",rideId));
            }
            req.setStatus("CANCELLED");
        }
        rideRequestRepository.saveAll(requests);
    }

    @Transactional
    public boolean startRide(int rideId, int riderId, int otp, String username) {
        requireRider(username, riderId);
        RideDetailsEntity ride = rideDetailsRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + rideId));

        if (ride.getRiderId() != riderId) {
            throw new InvalidRideStatusException("Rider is not assigned to this ride");
        }
        if ("AWAITING_PAYMENT".equals(ride.getRideStatus()) && ride.getRideOtp() == otp) return true;
        if (!"ACCEPTED".equals(ride.getRideStatus())) {
            throw new InvalidRideStatusException("Ride status must be ACCEPTED to start");
        }

        if (ride.getRideOtp() != otp) {
            throw new InvalidRideStatusException("OTP is incorrect");
        }

        ride.setRideStatus("AWAITING_PAYMENT");
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
                        "type", "PAYMENT_REQUIRED",
                        "rideId", rideId
                )
        );

        messagingTemplate.convertAndSend(
                "/topic/user/" + ride.getAdminId(),
                (Object) Map.of(
                        "type", "PAYMENT_REQUIRED",
                        "rideId", rideId
                )
        );
        messagingTemplate.convertAndSend("/topic/rider/"+riderId,
                (Object)Map.of("type","PAYMENT_REQUIRED","rideId",rideId));

        return true;
    }

    /**
     * End ride: mark as COMPLETED, make rider AVAILABLE, and create an invoice.
     * Invoice is marked as PENDING for settlement (payment processing can be done async).
     */
    @Transactional
    public boolean endRide(int rideId, int riderId, String username) {
        requireRider(username, riderId);
        RideDetailsEntity ride = rideDetailsRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + rideId));
        
        if (ride.getRiderId() != riderId) {
            throw new InvalidRideStatusException("Rider is not assigned to this ride");
        }
        if ("COMPLETED".equals(ride.getRideStatus())) return true;
        if (!"STARTED".equals(ride.getRideStatus()) || !paymentStore.isCaptured(rideId)) {
            throw new InvalidRideStatusException("Ride status must be STARTED to end");
        }

        // Mark ride as completed
        ride.setRideStatus("COMPLETED");
        rideDetailsRepository.save(ride);

        // Mark rider AVAILABLE
        paymentStore.enqueueCompleted(rideId);
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
                "PAID"              // customer payment captured; rider transfer tracked separately
        );
        invoiceRepository.save(invoice);

        // Notify both user and ride topic
        messagingTemplate.convertAndSend("/topic/rider/"+riderId,
                (Object)Map.of("type","RIDE_COMPLETED","rideId",rideId,"fare",ride.getFare(),"invoiceId",invoice.getInvoiceId()));
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
    
    
    /** Declining an offer is not cancellation of an assigned ride. Lock in the same order as acceptance. */
    @Transactional
    public void declineOffer(int rideId, String username) {
        var ride=rideDetailsRepository.findByIdForUpdate(rideId).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Offer not found"));
        var account=userRepository.findByUserName(username).orElseThrow();
        var rider=riderDemoRepository.findByUserId(account.getId()).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Offer not found"));
        var offer=rideRequestRepository.findByRideId(rideId).stream()
                .filter(q -> q.getRiderId()==rider.getId()).findFirst().orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Offer not found"));
        if ("CANCELLED".equals(offer.getStatus())) return; // Idempotent dismissal, including an already withdrawn offer.
        if (!"SENT".equals(offer.getStatus()) || !"REQUESTED".equals(ride.getRideStatus()) || ride.getRiderId()!=0)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Offer is no longer pending");
        offer.setStatus("CANCELLED");
        rideRequestRepository.save(offer);
        // Other offers, ride state, rider availability, payment and OTP are intentionally untouched.
        // The push worker rechecks this offer before dispatching any queued request.
    }

    private void requireRider(String username, int riderId) {
        var account = userRepository.findByUserName(username).orElseThrow();
        var rider = riderDemoRepository.findByUserId(account.getId()).orElseThrow();
        if (rider.getId() != riderId)
            throw new org.springframework.security.access.AccessDeniedException("A rider can act only for their own profile");
    }
    
    

}
