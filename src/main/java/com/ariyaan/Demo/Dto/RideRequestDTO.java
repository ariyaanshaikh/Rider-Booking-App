package com.ariyaan.Demo.Dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * RideRequestDTO - can be used as alternative to RideDetailsDTO for ride booking requests
 */
public class RideRequestDTO {

	private int rideId;

	@NotNull(message = "Pickup latitude is required")
	private Double pickUpLatitude;

	@NotNull(message = "Pickup longitude is required")
	private Double pickUpLongitude;

	@NotNull(message = "Drop latitude is required")
	private Double dropLatitude;

	@NotNull(message = "Drop longitude is required")
	private Double dropLongitude;

	@NotNull(message = "Fare is required")
	@Positive(message = "Fare must be positive")
	private Double fare;

	
	public int getRideId() {
		return rideId;
	}
	public void setRideId(int rideId) {
		this.rideId = rideId;
	}
	public double getPickUpLatitude() {
		return pickUpLatitude;
	}
	public void setPickUpLatitude(double pickUpLatitude) {
		this.pickUpLatitude = pickUpLatitude;
	}
	public double getPickUpLongitude() {
		return pickUpLongitude;
	}
	public void setPickUpLongitude(double pickUpLongitude) {
		this.pickUpLongitude = pickUpLongitude;
	}
	public double getDropLatitude() {
		return dropLatitude;
	}
	public void setDropLatitude(double dropLatitude) {
		this.dropLatitude = dropLatitude;
	}
	public double getDropLongitude() {
		return dropLongitude;
	}
	public void setDropLongitude(double dropLongitude) {
		this.dropLongitude = dropLongitude;
	}
	public double getFare() {
		return fare;
	}
	public void setFare(double fare) {
		this.fare = fare;
	}
	
	
	
}
