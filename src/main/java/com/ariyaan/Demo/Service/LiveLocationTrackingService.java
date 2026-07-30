package com.ariyaan.Demo.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import com.ariyaan.Demo.Dto.RiderLocationDTO;
import com.ariyaan.Demo.Dto.RiderLiveLocationUpdateDTO;
import com.ariyaan.Demo.Entity.RideDetailsEntity;
import com.ariyaan.Demo.Entity.RiderDemoEntity;
import com.ariyaan.Demo.Entity.User;
import com.ariyaan.Demo.Repository.RideDetailsRepository;
import com.ariyaan.Demo.Repository.RiderDemoRepository;
import com.ariyaan.Demo.Entity.RiderLocationEntity;
import com.ariyaan.Demo.Repository.RiderLocationRepository;
import com.ariyaan.Demo.Repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;

@Service
public class LiveLocationTrackingService {

	private final SimpMessagingTemplate messagingTemplate;
	
	@Autowired
	private RiderLocationRepository riderLocationRepository;

	private final UserRepository userRepository;
	private final RiderDemoRepository riderDemoRepository;
	private final RideDetailsRepository rideDetailsRepository;

	private final RiderMatchingService riderMatchingService;
	
	public LiveLocationTrackingService(SimpMessagingTemplate messagingTemplate,
			RiderMatchingService riderMatchingService, UserRepository userRepository,
			RiderDemoRepository riderDemoRepository, RideDetailsRepository rideDetailsRepository) {
		this.messagingTemplate = messagingTemplate;
		this.riderMatchingService = riderMatchingService;
		this.userRepository = userRepository;
		this.riderDemoRepository = riderDemoRepository;
		this.rideDetailsRepository = rideDetailsRepository;
	}
	
	public void sendLocationUpdate(RiderLocationDTO location) {
		messagingTemplate.convertAndSend(
				"/topic/ride/"+location.getRideId(),
				location);
		
	}

	// This will save riders current location in DB in Every 10-20 seconds
	
	public void saveRiderCurrentLocation(RiderLocationDTO location) {
	//	RiderLocationEntity riderLocationEntity  = new RiderLocationEntity();
		
		
		RiderLocationEntity riderLocationEntity = riderLocationRepository.findById(location.getRiderId())
		        .orElse(new RiderLocationEntity());
		
		
		riderLocationEntity.setRiderId(location.getRiderId());
		riderLocationEntity.setLatitude(location.getLatitude());
		riderLocationEntity.setLongitude(location.getLongitude());
		// Set the current time so you can track movement over time
	    riderLocationEntity.setUpdatedAt(new java.sql.Timestamp(System.currentTimeMillis()));
	    
	    riderLocationRepository.save(riderLocationEntity);
		
		
	}

	public java.util.List<Integer> findNearbyRiders(double latitude, double longitude) {
		return riderMatchingService.findNearestRiders(latitude, longitude);
	}

	/**
	 * Publishes a rider's GPS point only to the booking user's private queue.
	 * The client supplies location only; rider and active ride are derived from
	 * the authenticated account's RIDER_DETAILS.USER_ID mapping.
	 */
	public Map<String, Object> publishAuthenticatedRiderLocation(String username,
			RiderLiveLocationUpdateDTO location) {
		User account = userRepository.findByUserName(username)
				.orElseThrow(() -> new AccessDeniedException("Authenticated account was not found"));
		RiderDemoEntity rider = riderDemoRepository.findByUserId(account.getId())
				.orElseThrow(() -> new AccessDeniedException("This account is not linked to a rider profile"));

		RideDetailsEntity ride = rideDetailsRepository
				.findFirstByRiderIdAndRideStatusInOrderByRideIdDesc((int) rider.getId(),
						List.of("ACCEPTED", "STARTED"))
				.orElseThrow(() -> new AccessDeniedException("No active ride is assigned to this rider"));

		User bookingUser = userRepository.findById((long) ride.getAdminId())
				.orElseThrow(() -> new IllegalStateException("Booking user was not found"));

		RiderLocationEntity riderLocation = riderLocationRepository.findById((int) rider.getId())
				.orElse(new RiderLocationEntity());
		riderLocation.setRiderId((int) rider.getId());
		riderLocation.setLatitude(location.getLatitude());
		riderLocation.setLongitude(location.getLongitude());
		riderLocation.setUpdatedAt(new java.sql.Timestamp(System.currentTimeMillis()));
		riderLocationRepository.save(riderLocation);

		Map<String, Object> payload = Map.of(
				"type", "RIDER_LOCATION_UPDATED",
				"rideId", ride.getRideId(),
				"riderId", rider.getId(),
				"latitude", location.getLatitude(),
				"longitude", location.getLongitude());
		messagingTemplate.convertAndSendToUser(bookingUser.getUsername(),
				"/queue/ride/" + ride.getRideId() + "/location", payload);

		return payload;
	}

}
