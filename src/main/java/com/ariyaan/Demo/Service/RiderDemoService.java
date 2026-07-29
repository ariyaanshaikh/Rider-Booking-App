package com.ariyaan.Demo.Service;

import org.springframework.stereotype.Service;

import com.ariyaan.Demo.Dto.RiderDetailsDTO;
import com.ariyaan.Demo.Entity.RiderDemoEntity;

@Service
public interface RiderDemoService {

	public void sendRiderDetails();

	RiderDemoEntity createRider(RiderDetailsDTO riderDetails);
	
}
