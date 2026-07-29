package com.ariyaan.Demo.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ariyaan.Demo.Entity.RiderDemoEntity;

@Repository
public interface RiderDemoRepository  extends JpaRepository<RiderDemoEntity, Long>{

//	public void save(RiderDemoEntity riderDetial);
}
