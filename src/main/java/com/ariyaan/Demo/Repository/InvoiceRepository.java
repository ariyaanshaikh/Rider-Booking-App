package com.ariyaan.Demo.Repository;

import com.ariyaan.Demo.Entity.InvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * InvoiceRepository provides CRUD operations and custom queries for Invoice entities.
 */
@Repository
public interface InvoiceRepository extends JpaRepository<InvoiceEntity, Integer> {

	/**
	 * Find all invoices for a specific ride.
	 */
	List<InvoiceEntity> findByRideId(int rideId);

	/**
	 * Find all invoices for a specific user.
	 */
	List<InvoiceEntity> findByUserId(int userId);

	/**
	 * Find all invoices for a specific rider.
	 */
	List<InvoiceEntity> findByRiderId(int riderId);

	/**
	 * Find invoice by ride ID (usually one-to-one).
	 */
	Optional<InvoiceEntity> findFirstByRideIdOrderByCreatedAtDesc(int rideId);
}
