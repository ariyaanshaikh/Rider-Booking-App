package com.ariyaan.Demo.Controller;

import java.util.List;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.service.annotation.PutExchange;
import org.springframework.security.access.prepost.PreAuthorize;

import com.ariyaan.Demo.Dto.RideDetailsDTO;
import com.ariyaan.Demo.Dto.RideRequestDTO;
import com.ariyaan.Demo.Entity.RideDetailsEntity;
import com.ariyaan.Demo.Repository.RideDetailsRepository;
import com.ariyaan.Demo.Service.RideDetailsService;
import com.ariyaan.Demo.Service.RiderMatchingService;

/**
 * RideRequestController handles all ride booking and lifecycle operations:
 * - Create ride request
 * - Accept ride
 * - Verify OTP and start ride
 * - End ride
 * - Handle cancellations
 */
@RestController
@RequestMapping("/ride")
public class RideRequestController {
	
	private final RideDetailsService rideDetailsService;
	private final RiderMatchingService riderMatchingService;
	private final SimpMessagingTemplate messageTemplate;
	private final RideDetailsRepository rideDetailsRepository;
	private final com.ariyaan.Demo.Service.RideService rideService;

	public RideRequestController(RideDetailsService rideDetailsService,
			RiderMatchingService riderMatchingService,
			SimpMessagingTemplate messageTemplate,
			RideDetailsRepository rideDetailsRepository,
			com.ariyaan.Demo.Service.RideService rideService) {
		this.rideDetailsService = rideDetailsService;
		this.riderMatchingService = riderMatchingService;
		this.messageTemplate = messageTemplate;
		this.rideDetailsRepository = rideDetailsRepository;
		this.rideService = rideService;
	}

	/**
	 * Request a new ride. Server will:
	 * 1. Save RideDetails
	 * 2. Generate secure 4-digit ride-start OTP (no time expiry)
	 * 3. Find nearest 5 available riders
	 * 4. Create RideRequest rows
	 * 5. Notify riders via WebSocket
	 */
	@PostMapping("/request")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<String> requestRide(@Valid @RequestBody RideDetailsDTO rideRequest,
			org.springframework.security.core.Authentication authentication) {
		int rideId = rideDetailsService.saveRideDetials(rideRequest, authentication.getName());
		return ResponseEntity.ok("Ride Request Sent, id=" + rideId);
	}
	
	/**
	 * Rider accepts the ride.
	 * Only the first accepting rider succeeds (atomic update).
	 * Other riders are notified their request is cancelled.
	 */
	@PostMapping("/accept")
	@PreAuthorize("hasRole('RIDER')")
	public ResponseEntity<String> acceptRide(
			@RequestParam int rideId,
			@RequestParam int riderId,
			org.springframework.security.core.Authentication authentication) {

		boolean success = rideService.acceptRide(rideId, riderId, authentication.getName());
		if (success) {
			return ResponseEntity.ok("Ride accepted");
		}
		return ResponseEntity.badRequest().body("Ride already taken");
	}

	/**
	 * Rider cancels after accepting.
	 * Triggers rematch with next nearest riders.
	 */
	@PostMapping("/rider-cancel")
	@PreAuthorize("hasRole('RIDER')")
	public ResponseEntity<String> riderCancel(@RequestParam int rideId, @RequestParam int riderId,
			org.springframework.security.core.Authentication authentication) {
		rideService.riderCancel(rideId, riderId, authentication.getName());
		return ResponseEntity.ok("Rider cancellation handled and rematch attempted");
	}

	/**
	 * User/admin cancels the ride.
	 */
	@PostMapping("/cancel")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<String> userCancel(@RequestParam int rideId,
			org.springframework.security.core.Authentication authentication) {
		rideService.userCancel(rideId, authentication.getName());
		return ResponseEntity.ok("Ride cancelled by user/admin");
	}
	
	/**
	 * Verify OTP and start the ride.
	 * Only allowed when:
	 * - Ride status is ACCEPTED
	 * - Rider ID matches assigned rider
	 * - OTP is correct
	 * On success, marks ride as STARTED and notifies user/rider.
	 */
	@PostMapping("/verify-otp")
	@PreAuthorize("hasRole('RIDER')")
	public ResponseEntity<String> verifyOtp(
	        @RequestParam int rideId,
			@RequestParam int riderId,
			@RequestParam int otp, org.springframework.security.core.Authentication authentication) {

		boolean started = rideService.startRide(rideId, riderId, otp, authentication.getName());
		if (started) {
			return ResponseEntity.ok("OTP verified. Payment required before ride starts");
		}
		return ResponseEntity.badRequest().body("Invalid OTP or ride state");
	}

	/**
	 * End the ride.
	 * Marks ride as COMPLETED, rider as AVAILABLE, and creates invoice.
	 */
	@PostMapping("/end")
	@PreAuthorize("hasRole('RIDER')")
	public ResponseEntity<String> endRide(@RequestParam int rideId, @RequestParam int riderId,
			org.springframework.security.core.Authentication authentication) {
		boolean ended = rideService.endRide(rideId, riderId, authentication.getName());
		if (ended) {
			return ResponseEntity.ok("Ride ended successfully");
		}
		return ResponseEntity.badRequest().body("Unable to end ride");
	}
	
	@PutMapping("/paymentCompleted")
		public String paymentCompleted(@RequestParam int rideId,@RequestParam String paymentCompleted, @RequestBody String paymentJson) {
			throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,
				"Use Razorpay verification; client-supplied payment status is disabled");
		}
	
}


