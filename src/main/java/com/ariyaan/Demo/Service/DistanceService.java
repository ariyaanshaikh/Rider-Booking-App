package com.ariyaan.Demo.Service;

import com.google.maps.DistanceMatrixApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DistanceMatrix;
import com.google.maps.model.LatLng;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import com.google.maps.model.DistanceMatrixElementStatus;

@Service
public class DistanceService {

    private final String apiKey;
    public DistanceService(@Value("${google.maps.api-key:}") String apiKey) { this.apiKey = apiKey; }

    public long getDistanceMeters(double startLat, double startLng, double endLat, double endLng) {
        if (!Double.isFinite(startLat) || !Double.isFinite(endLat) || !Double.isFinite(startLng) || !Double.isFinite(endLng)
                || Math.abs(startLat) > 90 || Math.abs(endLat) > 90 || Math.abs(startLng) > 180 || Math.abs(endLng) > 180)
            throw new IllegalArgumentException("Invalid route coordinates");
        if (apiKey.isBlank()) throw new IllegalStateException("GOOGLE_MAPS_API_KEY is required for fare calculation");
        if (startLat == endLat && startLng == endLng) throw new IllegalArgumentException("Pickup and destination must differ");
        GeoApiContext context = new GeoApiContext.Builder().apiKey(apiKey)
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build();
        try {
            DistanceMatrix matrix = DistanceMatrixApi.newRequest(context)
                    .origins(new LatLng(startLat, startLng)).destinations(new LatLng(endLat, endLng)).await();
            if (matrix.rows.length == 0 || matrix.rows[0].elements.length == 0
                    || matrix.rows[0].elements[0].status != DistanceMatrixElementStatus.OK
                    || matrix.rows[0].elements[0].distance == null)
                throw new IllegalStateException("Google did not return a usable driving route");
            long meters = matrix.rows[0].elements[0].distance.inMeters;
            if (meters <= 0) throw new IllegalStateException("Route distance must be positive");
            return meters;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Distance request interrupted");
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate Google route distance");
        } finally { context.shutdown(); }
    }

    public String getDistance(double startLat, double startLng, double endLat, double endLng) {
        return java.math.BigDecimal.valueOf(getDistanceMeters(startLat, startLng, endLat, endLng), 3).toPlainString() + " km";

    }
}
