package com.ariyaan.Demo.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;

import com.ariyaan.Demo.Entity.RiderDemoEntity;

@Repository
public interface RiderDemoRepository  extends JpaRepository<RiderDemoEntity, Long>{

	Optional<RiderDemoEntity> findByUserId(Long userId);

	/** Serializes ride-accept attempts for one rider. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select rider from RiderDemoEntity rider where rider.id = :riderId")
	Optional<RiderDemoEntity> findByIdForUpdate(@Param("riderId") Long riderId);

//	public void save(RiderDemoEntity riderDetial);
}
