package com.ariyaan.Demo.Service;

import com.google.maps.DistanceMatrixApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DistanceMatrix;
import com.google.maps.model.LatLng;
import org.springframework.stereotype.Service;

@Service
public class DistanceService {

    private final String API_KEY = "AIzaSyBKrK9maTXeNBkJHYN2_ZxAuBTEU4JHkBc";

    public String getDistance(double startLat, double startLng, double endLat, double endLng) {
        GeoApiContext context = new GeoApiContext.Builder()
                .apiKey(API_KEY)
                .build();

        LatLng origin = new LatLng(startLat, startLng);
        LatLng destination = new LatLng(endLat, endLng);

        try {
            DistanceMatrix matrix = DistanceMatrixApi.newRequest(context)
                    .origins(origin)
                    .destinations(destination)
                    .await();

            // Check if the response actually contains data
            if (matrix.rows.length > 0 && matrix.rows[0].elements.length > 0) {
                var element = matrix.rows[0].elements[0];

                if (element.status.toString().equals("OK")) {
                    // Use humanReadable for the "10 km" string
                    return element.distance.humanReadable; 
                } else {
                    return "Route error: " + element.status;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "No data found";

    }
}
