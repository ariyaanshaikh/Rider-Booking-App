package com.ariyaan.Demo.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ariyaan.Demo.Entity.RiderLocationEntity;

@Repository
public interface RiderMatchingRepository extends JpaRepository<RiderLocationEntity, Integer> {
/*
    @Query(value = """
        SELECT rider_id, lat, lon,
        (
            6371 * acos(
                cos(radians(:lat)) *
                cos(radians(rl.CURRENT_LATITUDE)) *
                cos(radians(rl.CURRENT_LONGITUDE) - radians(:lon)) +
                sin(radians(:lat)) *
                sin(radians(rl.CURRENT_LATITUDE))
            )
        ) AS distance
        FROM rider_location rl
        WHERE rl.updated_at > NOW() - INTERVAL '60 seconds'
        ORDER BY distance
        LIMIT 20
    """, nativeQuery = true)
    List<Object[]> findNearestRiders(double lat, double lon); */
	
	@Query(value = """
			/*
			 * Find every AVAILABLE rider within 10 km. Locations older than 10
			 * minutes are stale and deliberately excluded from matching.
			 */
			SELECT rider_id, current_latitude, current_longitude, distance
			FROM (
				SELECT rl.rider_id, rl.current_latitude, rl.current_longitude,
					(
						6371 * acos(least(1.0, greatest(-1.0,
							cos(radians(:lat)) * cos(radians(rl.current_latitude)) *
							cos(radians(rl.current_longitude) - radians(:lon)) +
							sin(radians(:lat)) * sin(radians(rl.current_latitude))
						)))
					) AS distance
				FROM rider_location rl
				JOIN rider_details rd ON rd.id = rl.rider_id
				WHERE rl.updated_at > NOW() - INTERVAL '10 minutes'
					AND rd.status = 'AVAILABLE'
			) candidates
			WHERE distance <= 10
			ORDER BY distance
		""", nativeQuery = true)
	List<Object[]> findNearestRiders(@Param("lat") double lat, @Param("lon") double lon);

}
