package com.ariyaan.Demo.Service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.ariyaan.Demo.Entity.OsrmResponse;

@Service
public class DistanceCalculationService {

    // OSRM Demo server (Note: For production, host your own OSRM instance)
    private final String OSRM_URL = "http://router.project-osrm.org/route/v1/driving/";

    public double getDistanceInKm(double startLat, double startLon, double endLat, double endLon) {
        RestTemplate restTemplate = new RestTemplate();
        
        // OSRM expects: longitude,latitude;longitude,latitude
        String url = String.format("%s%f,%f;%f,%f?overview=false", 
                                    OSRM_URL, startLon, startLat, endLon, endLat);

        try {
            OsrmResponse response = restTemplate.getForObject(url, OsrmResponse.class);
            
            if (response != null && !response.routes.isEmpty()) {
                // Convert meters to kilometers
                return response.routes.get(0).distance / 1000.0;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate distance from OSRM: " + e.getMessage());
        }
        return 0.0;
    }
}
