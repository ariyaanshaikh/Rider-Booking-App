package com.ariyaan.Demo.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * InvoiceEntity represents a ride invoice/receipt generated after ride completion.
 * Stores billing information for a completed ride including amount and settlement status.
 */
@Entity
@Table(name = "INVOICES")
public class InvoiceEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "INVOICE_ID")
	private int invoiceId;

	@Column(name = "RIDE_ID", nullable = false)
	private int rideId;

	@Column(name = "RIDER_ID", nullable = false)
	private int riderId;

	@Column(name = "USER_ID", nullable = false)
	private int userId;

	@Column(name = "AMOUNT", nullable = false)
	private double amount;

	@Column(name = "CREATED_AT", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "STATUS", nullable = false)
	private String status; // PENDING, SETTLED, REFUNDED

	// Constructors
	public InvoiceEntity() {
	}

	public InvoiceEntity(int rideId, int riderId, int userId, double amount, LocalDateTime createdAt, String status) {
		this.rideId = rideId;
		this.riderId = riderId;
		this.userId = userId;
		this.amount = amount;
		this.createdAt = createdAt;
		this.status = status;
	}

	// Getters and Setters
	public int getInvoiceId() {
		return invoiceId;
	}

	public void setInvoiceId(int invoiceId) {
		this.invoiceId = invoiceId;
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

	public int getUserId() {
		return userId;
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}

	public double getAmount() {
		return amount;
	}

	public void setAmount(double amount) {
		this.amount = amount;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
}
