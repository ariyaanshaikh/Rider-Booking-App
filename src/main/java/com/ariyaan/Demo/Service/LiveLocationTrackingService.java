package com.ariyaan.Demo.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.ariyaan.Demo.Dto.RiderLocationDTO;
import com.ariyaan.Demo.Entity.RiderLocationEntity;
import com.ariyaan.Demo.Repository.RiderLocationRepository;

@Service
public class LiveLocationTrackingService {

	private final SimpMessagingTemplate messagingTemplate;
	
	@Autowired
	private RiderLocationRepository riderLocationRepository;

	private final RiderMatchingService riderMatchingService;
	
	public LiveLocationTrackingService(SimpMessagingTemplate messagingTemplate,
			RiderMatchingService riderMatchingService) {
		this.messagingTemplate = messagingTemplate;
		this.riderMatchingService = riderMatchingService;
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
	    
	//	riderLocationRepository.save(riderLocationEntity);
	    riderLocationRepository.updateRiderLocation(location.getRiderId(), location.getLatitude(), location.getLongitude());
		
		
	}

	public java.util.List<Integer> findNearbyRiders(double latitude, double longitude) {
		return riderMatchingService.findNearestRiders(latitude, longitude);
	}

}
