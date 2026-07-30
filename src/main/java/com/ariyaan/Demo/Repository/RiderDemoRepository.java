package com.ariyaan.Demo.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import com.ariyaan.Demo.Entity.RiderDemoEntity;

@Repository
public interface RiderDemoRepository  extends JpaRepository<RiderDemoEntity, Long>{

	Optional<RiderDemoEntity> findByUserId(Long userId);

//	public void save(RiderDemoEntity riderDetial);
}
