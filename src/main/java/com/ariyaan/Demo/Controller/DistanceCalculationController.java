package com.ariyaan.Demo.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.ariyaan.Demo.Service.DistanceCalculationService;
import com.ariyaan.Demo.Service.DistanceService;

@RestController
@RequestMapping("/api/fare")
public class DistanceCalculationController {

    private final DistanceCalculationService DistanceCalculationService;
    
    
    private final DistanceService distanceService;

    public DistanceCalculationController(DistanceCalculationService DistanceCalculationService, DistanceService distanceService) {
        this.DistanceCalculationService = DistanceCalculationService;
		this.distanceService = distanceService;
    }
    

    @GetMapping("/calculate")
    public String calculateFare(
            @RequestParam double sLat, @RequestParam double sLon,
            @RequestParam double dLat, @RequestParam double dLon) {
        
    	
    // using goole map	
    String distance = distanceService.getDistance(sLat, sLon, dLat, dLon);
    
    // using free map
     //   double distance = DistanceCalculationService.getDistanceInKm(sLat, sLon, dLat, dLon);
        
        // Simple Pricing Logic: 50 base + 12 per km
      //  double totalFare = 50 + (distance * 12);
        
        return String.format("Distance: %.2s km | Total Fare: ₹%.2s", distance, "Need to Calculate the fare");
    }
}
