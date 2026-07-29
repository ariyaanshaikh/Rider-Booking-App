package com.ariyaan.Demo.Dto;

import jakarta.validation.constraints.NotNull;

public class RiderLocationDTO {

	@NotNull(message = "Ride ID is required")
	private Integer rideId;

	@NotNull(message = "Rider ID is required")
	private Integer riderId;

	@NotNull(message = "Latitude is required")
	private Double latitude;

	@NotNull(message = "Longitude is required")
	private Double longitude;

	public RiderLocationDTO() {
	}

	public RiderLocationDTO(Integer rideId, Integer riderId, Double latitude, Double longitude) {
		this.rideId = rideId;
		this.riderId = riderId;
		this.latitude = latitude;
		this.longitude = longitude;
	}

	public int getRideId() {
		return rideId;
	}
	public void setRideId(int rideId) {
		this.rideId = rideId;
	}
	public int getRiderId() {
		return riderId;
	}
	public void setRiderId(int riderId) {
		this.riderId = riderId;
	}
	public double getLatitude() {
		return latitude;
	}
	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}
	public double getLongitude() {
		return longitude;
	}
	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}
	
	
	
	
}
