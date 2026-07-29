package com.ariyaan.Demo.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ariyaan.Demo.Entity.RideDetailsEntity;
import com.ariyaan.Demo.Entity.RiderLocationEntity;

import jakarta.transaction.Transactional;

@Repository
public interface RiderLocationRepository  extends JpaRepository<RiderLocationEntity, Integer>{

//	public void save(RiderDemoEntity riderDetial);
	
//	void save(RiderLocationEntity riderLocation);
	
	@Modifying
	@Transactional
	@Query(value = """
	    INSERT INTO RIDER_LOCATION (rider_id, current_latitude, current_longitude, updated_at)
	    VALUES (:id, :lat, :lon, CURRENT_TIMESTAMP)
	    ON CONFLICT (rider_id) 
	    DO UPDATE SET 
	        current_latitude = EXCLUDED.current_latitude, 
	        current_longitude = EXCLUDED.current_longitude, 
	        updated_at = CURRENT_TIMESTAMP
	""", nativeQuery = true)
	void updateRiderLocation(@Param("id") int id, @Param("lat") double lat, @Param("lon") double lon);

}
