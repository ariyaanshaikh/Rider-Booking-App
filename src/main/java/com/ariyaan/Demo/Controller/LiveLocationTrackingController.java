package com.ariyaan.Demo.Controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.validation.Valid;

import com.ariyaan.Demo.Dto.RiderDetailsDTO;
import com.ariyaan.Demo.Dto.RiderLiveLocationUpdateDTO;
import com.ariyaan.Demo.Entity.RiderDemoEntity;
import com.ariyaan.Demo.Service.LiveLocationTrackingService;
import com.ariyaan.Demo.Service.RiderDemoService;
import com.ariyaan.Demo.Service.RiderProfileService;
import com.ariyaan.Demo.Dto.RiderVerificationDetailsDTO;
import com.ariyaan.Demo.Entity.RiderVerificationDetailsEntity;
import com.ariyaan.Demo.Entity.RideDetailsEntity;

/*  

This File is for  tracking of the Rider by Admin through websocket

 - rider sends their location every 10 seconds 

POST /rider/location
{
  riderId: 12
  lat: 19.07
  lon: 72.87
}

we can store it in db in this way 
CREATE TABLE rider_location (
  rider_id INT,
  location GEOGRAPHY(POINT),
  updated_at TIMESTAMP
);

INSERT INTO rider_location
VALUES (101, ST_SetSRID(ST_MakePoint(72.8777,19.0760),4326));

-  And find neared rider 
SELECT rider_id
FROM rider_location
ORDER BY location <-> ST_SetSRID(ST_MakePoint(72.870,19.075),4326)
LIMIT 5;


 */


@RestController
@RequestMapping("/rider")
public class LiveLocationTrackingController {

	private final LiveLocationTrackingService liveLocationTrackingService;
	private final RiderDemoService riderDemoService;
	private final RiderProfileService riderProfileService;
	
	public LiveLocationTrackingController(LiveLocationTrackingService liveLocationTrackingService,
			RiderDemoService riderDemoService, RiderProfileService riderProfileService) {
		this.liveLocationTrackingService = liveLocationTrackingService;
		this.riderDemoService = riderDemoService;
		this.riderProfileService = riderProfileService;
	}

	@PostMapping("/{riderId}/verification-details")
	@PreAuthorize("@riderAccess.owns(authentication, #riderId) or @riderAccess.operator(authentication)")
	public ResponseEntity<RiderVerificationDetailsEntity> saveVerificationDetails(
			@PathVariable long riderId, @Valid @RequestBody RiderVerificationDetailsDTO details) {
		return ResponseEntity.status(HttpStatus.CREATED).body(riderProfileService.saveDetails(riderId, details));
	}

	@GetMapping("/{riderId}/verification-details")
	@PreAuthorize("@riderAccess.owns(authentication, #riderId) or @riderAccess.operator(authentication)")
	public RiderVerificationDetailsEntity getVerificationDetails(@PathVariable long riderId) {
		return riderProfileService.getDetails(riderId);
	}

	@PatchMapping("/{riderId}/verification")
	@PreAuthorize("@riderAccess.operator(authentication)")
	public RiderVerificationDetailsEntity setVerification(@PathVariable long riderId,
			@RequestParam boolean verified) {
		return riderProfileService.setVerified(riderId, verified);
	}

	@GetMapping("/{riderId}/history")
	@PreAuthorize("@riderAccess.owns(authentication, #riderId) or @riderAccess.operator(authentication)")
	public List<RideDetailsEntity> riderHistory(@PathVariable long riderId) {
		return riderProfileService.getHistory(riderId);
	}

	@PostMapping
	@PreAuthorize("hasRole('RIDER')")
	public ResponseEntity<RiderDemoEntity> createRider(Authentication authentication,
			@Valid @RequestBody RiderDetailsDTO riderDetails) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(riderDemoService.createRider(authentication.getName(), riderDetails));
	}
	
	// To livetrack the rider and show it to the admin
	
	@PostMapping("/fetch/livelocation")
	@PreAuthorize("hasRole('RIDER')")
	public ResponseEntity<java.util.Map<String, Object>> updateLocation(
			Authentication authentication, @Valid @RequestBody RiderLiveLocationUpdateDTO location) {
		return ResponseEntity.ok(liveLocationTrackingService
				.publishAuthenticatedRiderLocation(authentication.getName(), location));
	}
	
	/** Stores the rider's pre-ride GPS point so the matching service can find them. */
	@PostMapping("/save/location")
	@PreAuthorize("hasRole('RIDER')")
	public ResponseEntity<java.util.Map<String, Object>> saveLocation(
			Authentication authentication, @Valid @RequestBody RiderLiveLocationUpdateDTO location) {
		return ResponseEntity.ok(liveLocationTrackingService
				.saveAuthenticatedRiderCurrentLocation(authentication.getName(), location));
	}

	/** Rider can opt in or out of dispatch; ON_TRIP is managed by ride lifecycle. */
	@PatchMapping("/status")
	@PreAuthorize("hasRole('RIDER')")
	public ResponseEntity<java.util.Map<String, Object>> updateMyStatus(
			Authentication authentication, @org.springframework.web.bind.annotation.RequestParam String status) {
		return ResponseEntity.ok(liveLocationTrackingService
				.updateAuthenticatedRiderAvailability(authentication.getName(), status));
	}

	@GetMapping("/nearby")
	@PreAuthorize("hasRole('ADMIN')")
	public List<Integer> findNearbyRiders(
			@RequestParam double lat,
			@RequestParam double lon) {
		return liveLocationTrackingService.findNearbyRiders(lat, lon);
	}
}
