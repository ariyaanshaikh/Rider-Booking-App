package com.ariyaan.Demo.Dto;

import jakarta.validation.constraints.NotNull;

/** Location payload sent by an authenticated rider. Ride and rider identity are derived server-side. */
public class RiderLiveLocationUpdateDTO {

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
}
