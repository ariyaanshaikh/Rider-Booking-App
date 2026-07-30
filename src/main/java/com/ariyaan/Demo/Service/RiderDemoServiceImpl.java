package com.ariyaan.Demo.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ariyaan.Demo.Entity.RiderDemoEntity;
import com.ariyaan.Demo.Entity.User;
import com.ariyaan.Demo.Entity.UserRole;
import com.ariyaan.Demo.Dto.RiderDetailsDTO;
import com.ariyaan.Demo.Repository.RiderDemoRepository;
import com.ariyaan.Demo.Repository.UserRepository;

@Service
public class RiderDemoServiceImpl implements RiderDemoService{

	@Autowired
	private RiderDemoRepository riderDemoRepository;

	@Autowired
	private UserRepository userRepository;

	
	
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
		User account = userRepository.findById(riderDetails.getUserId())
				.orElseThrow(() -> new IllegalArgumentException("No user found for userId " + riderDetails.getUserId()));
		if (account.getRole() != UserRole.RIDER) {
			throw new IllegalArgumentException("RIDER_DETAILS can only be linked to a user with role RIDER");
		}

		RiderDemoEntity rider = new RiderDemoEntity();
		rider.setName(riderDetails.getName());
		rider.setMobile_number(riderDetails.getMobileNumber());
		rider.setStatus(riderDetails.getStatus());
		rider.setUserId(riderDetails.getUserId());
		return riderDemoRepository.save(rider);
	}
}
