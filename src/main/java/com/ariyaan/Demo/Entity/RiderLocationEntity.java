package com.ariyaan.Demo.Entity;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "RIDER_LOCATION")
public class RiderLocationEntity {
	

	@Column(name = "rider_id")
	@Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
	private int riderId;
	
	@Column(name = "current_latitude")
	private double latitude;
	
	@Column(name = "current_longitude")
	private double longitude;
	
	@Column(name = "updated_at")
	private Timestamp updatedAt;

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

	public Timestamp getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Timestamp updatedAt) {
		this.updatedAt = updatedAt;
	}
	
	
}
