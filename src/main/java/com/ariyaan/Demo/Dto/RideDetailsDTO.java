package com.ariyaan.Demo.Dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class RideDetailsDTO {

	private int rideId;

	@NotNull(message = "Pickup latitude is required")
		@jakarta.validation.constraints.DecimalMin("-90")
		@jakarta.validation.constraints.DecimalMax("90")
	private Double pickUpLatitude;

	@NotNull(message = "Pickup longitude is required")
		@jakarta.validation.constraints.DecimalMin("-180")
		@jakarta.validation.constraints.DecimalMax("180")
	private Double pickUpLongitude;

	@NotNull(message = "Drop latitude is required")
		@jakarta.validation.constraints.DecimalMin("-90")
		@jakarta.validation.constraints.DecimalMax("90")
	private Double dropLatitude;

	@NotNull(message = "Drop longitude is required")
		@jakarta.validation.constraints.DecimalMin("-180")
		@jakarta.validation.constraints.DecimalMax("180")
	private Double dropLongitude;

	/** Legacy input is ignored; the server calculates the fare from Google distance. */
	private Double fare;

	@NotBlank(message = "Chassis number is required")
	@Size(max = 100, message = "Chassis number must be at most 100 characters")
	private String chassisNo;

	@NotBlank(message = "Vehicle name is required")
	@Size(max = 150, message = "Vehicle name must be at most 150 characters")
	private String vehicleName;

	private int riderId;

	/** Legacy input is ignored; the authenticated ADMIN owns the booking. */
	private Integer adminId;

	private long rideOtp;
	private String rideStatus;
	
	// Constructors
	public RideDetailsDTO() {
	}

	public RideDetailsDTO(Double pickUpLatitude, Double pickUpLongitude, 
			Double dropLatitude, Double dropLongitude, Double fare, Integer adminId) {
		this.pickUpLatitude = pickUpLatitude;
		this.pickUpLongitude = pickUpLongitude;
		this.dropLatitude = dropLatitude;
		this.dropLongitude = dropLongitude;
		this.fare = fare;
		this.adminId = adminId;
	}
	
	// Getters and Setters
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
	public String getChassisNo() {
		return chassisNo;
	}
	public void setChassisNo(String chassisNo) {
		this.chassisNo = chassisNo;
	}
	public String getVehicleName() {
		return vehicleName;
	}
	public void setVehicleName(String vehicleName) {
		this.vehicleName = vehicleName;
	}
	public int getRiderId() {
		return riderId;
	}
	public void setRiderId(int riderId) {
		this.riderId = riderId;
	}
	public int getAdminId() {
		return adminId;
	}
	public void setAdminId(int adminId) {
		this.adminId = adminId;
	}
	public long getRideOtp() {
		return rideOtp;
	}
	public void setRideOtp(long rideOtp) {
		this.rideOtp = rideOtp;
	}
	public String getRideStatus() {
		return rideStatus;
	}
	public void setRideStatus(String rideStatus) {
		this.rideStatus = rideStatus;
	}
	
	
}
