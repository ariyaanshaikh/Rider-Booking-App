package com.ariyaan.Demo.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ariyaan.Demo.Entity.User;
import com.ariyaan.Demo.Repository.UserRepository;

@Service
public class UserDetials {

	@Autowired
	private UserRepository userRepository;
	
	@Autowired
	private PasswordEncoder passwordEncoder;
	
	public void registerUser(User user) {
		

		    Optional<User> existingUser =  userRepository.findByUserNameOrEmail(user.getUsername(), user.getEmail());
		    existingUser.ifPresent(t -> { t.setPassword(passwordEncoder.encode(user.getPassword()));
	    	userRepository.save(t);
	    	});
		    
		    // 5. Send Email logic here...
			
		}
	
	
	
}
