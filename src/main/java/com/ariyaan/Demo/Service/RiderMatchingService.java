package com.ariyaan.Demo.Service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ariyaan.Demo.Repository.RiderMatchingRepository;

@Service
public class RiderMatchingService {

	private final RiderMatchingRepository repository;

    public RiderMatchingService(RiderMatchingRepository repository) {
        this.repository = repository;
    }

    // To find the neared rider and returnt their Rider_ID
    
    public List<Integer> findNearestRiders(double lat, double lon) {

        List<Object[]> results = repository.findNearestRiders(lat, lon);

        return results.stream()
                .map(r -> ((Number) r[0]).intValue())
                .toList();
    }
    
}
