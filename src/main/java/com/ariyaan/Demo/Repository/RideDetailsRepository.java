package com.ariyaan.Demo.Repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ariyaan.Demo.Dto.RideDetailsDTO;
import com.ariyaan.Demo.Entity.RideDetailsEntity;
import com.ariyaan.Demo.Entity.RiderDemoEntity;
import com.ariyaan.Demo.Entity.RiderLocationEntity;


@Repository
public interface RideDetailsRepository  extends JpaRepository<RideDetailsEntity, Integer>{
	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RideDetailsEntity r where r.rideId=:rideId")
	Optional<RideDetailsEntity> findByIdForUpdate(@Param("rideId") int rideId);

//	public void save(RiderDemoEntity riderDetial);
	
//	void save(RiderLocationEntity riderLocation);
	/*
	@Modifying
    @Transactional
    @Query("""
        UPDATE RIDE_DETAILS r
        SET r.RIDER_ID = :riderId,
            r.RIDE_STATUS = 'ACCEPTED'
        WHERE r.RIDE_ID = :rideId
        AND r.RIDER_ID IS NULL
    """)
    int acceptRide(int rideId, int riderId);
	*/
	@Modifying
	@Transactional
	@Query("""
	    UPDATE RideDetailsEntity r
	    SET r.riderId = :riderId,
	        r.rideStatus = 'ACCEPTED'
	    WHERE r.rideId = :rideId
	    AND (r.riderId IS NULL OR r.riderId = 0)
	    AND r.rideStatus = 'REQUESTED'
	""")
	int acceptRide(@Param("rideId") int rideId, @Param("riderId") int riderId);


	 List<RideDetailsEntity> findByRideId(int rideId);

	 List<RideDetailsEntity> findByRiderIdOrderByRideIdDesc(int riderId);

	 List<RideDetailsEntity> findByAdminIdOrderByRideIdDesc(int adminId);

	Optional<RideDetailsEntity> findFirstByRiderIdAndRideStatusInOrderByRideIdDesc(
			 int riderId, Collection<String> rideStatuses);

	boolean existsByRiderIdAndRideStatusIn(int riderId, Collection<String> rideStatuses);

	 Optional<RideDetailsEntity> findFirstByAdminIdAndRideStatusInOrderByRideIdDesc(
			 int adminId, Collection<String> rideStatuses);
	 
	 
	 // TODO need to create new column in RIDE_DETAILS table with column > createdAt
	 /*
	 
	 @Modifying
	 @Transactional
	 @Query("DELETE FROM RIDE_DETAILS r WHERE r.createdAt < CURRENT_TIMESTAMP - 10")
	 void deleteOldRequests();
	 
	 */
	 
	 
	 
	 
	 
}
