package com.ariyaan.Demo.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ariyaan.Demo.Entity.RiderDemoEntity;
import com.ariyaan.Demo.Dto.RiderDetailsDTO;
import com.ariyaan.Demo.Repository.RiderDemoRepository;

@Service
public class RiderDemoServiceImpl implements RiderDemoService{

	@Autowired
	private RiderDemoRepository riderDemoRepository;

	
	
	public void sendRiderDetails() {

		RiderDemoEntity riderDemoEntity = new RiderDemoEntity(); 
		
	//	riderDemoEntity.setId(1);
		riderDemoEntity.setName("Ariyaan");
		riderDemoEntity.setMobile_number("9167");
		riderDemoEntity.setStatus("AVAILABLE");

		riderDemoRepository.save(riderDemoEntity);
	}

	@Override
	public RiderDemoEntity createRider(RiderDetailsDTO riderDetails) {
		RiderDemoEntity rider = new RiderDemoEntity();
		rider.setName(riderDetails.getName());
		rider.setMobile_number(riderDetails.getMobileNumber());
		rider.setStatus(riderDetails.getStatus());
		rider.setUserId(riderDetails.getUserId());
		return riderDemoRepository.save(rider);
	}
}
