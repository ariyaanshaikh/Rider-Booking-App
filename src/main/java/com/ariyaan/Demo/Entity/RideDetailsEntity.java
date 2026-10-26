package com.ariyaan.Demo.Entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "RIDE_DETAILS")
public class RideDetailsEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "RIDE_ID")
	private int rideId;
	
	@Column(name = "PICKUP_LATITUDE")
	private double pickUpLatitude;
	
	@Column(name = "PICKUP_LONGITUDE")
	private double pickUpLongitude;
	
	@Column(name = "DROP_LATITUDE")
	private double dropLatitude;
	
	@Column(name = "DROP_LONGITUDE")
	private double dropLongitude;
	
	@Column(name = "FARE")
	private double fare;

	@Column(name = "DISTANCE_METERS")
	private Long distanceMeters;
	public Long getDistanceMeters() { return distanceMeters; }
	public void setDistanceMeters(Long value) { distanceMeters = value; }

	@Column(name = "CHASSIS_NO")
	private String chassisNo;

	@Column(name = "VEHICLE_NAME")
	private String vehicleName;
	
	@Column(name = "RIDER_ID")
	private int riderId;
	
	@Column(name = "ADMIN_ID")
	private int adminId;
	
	@Column(name = "RIDE_OTP")
	private long rideOtp;
	
	@Column(name = "RIDE_STATUS")
	private String rideStatus;
	
	@Column(name = "PAYMENT_COMPLETED")
	private String paymentCompleted;
	
//	@Column(name = "PAYMENT_JSON")
//	private String paymentJson;
	
	
	@JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "PAYMENT_JSON", columnDefinition = "jsonb")
    private JsonNode paymentJson;
	
	
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
	public String getPaymentCompleted() {
		return paymentCompleted;
	}
	public void setPaymentCompleted(String paymentCompleted) {
		this.paymentCompleted = paymentCompleted;
	}
	public JsonNode getPaymentJson() {
		return paymentJson;
	}
	public void setPaymentJson(JsonNode paymentJson) {
		this.paymentJson = paymentJson;
	}
	
	
}
