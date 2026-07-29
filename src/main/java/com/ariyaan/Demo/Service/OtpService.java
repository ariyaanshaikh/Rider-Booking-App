package com.ariyaan.Demo.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ariyaan.Demo.Entity.User;
import com.ariyaan.Demo.Repository.UserRepository;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
public class OtpService {

    private final Map<String, String> otpStorage = new HashMap<>();
    private final Random random = new SecureRandom();
    
    @Autowired
    private UserRepository userRepository;
    
    

    public String generateOtp(String key) {
        String otp = String.format("%06d", random.nextInt(1000000));
        otpStorage.put(key, otp);
        return otp;
    }
    
    // otp to generate for email
    public String generateOtp() {
        String otp = String.format("%06d", random.nextInt(1000000));
        return otp;
    }
    

    public boolean validateOtp(String key, String otp) {
        return otp.equals(otpStorage.getOrDefault(key, ""));
    }
    
    // To save Mobile in Db 
    
    public void saveMobileNumber(String mobileNumber, String otp, String userName) {
    	
    	Optional<User> userdetails =  userRepository.findByUserName(userName);
    	userdetails.ifPresent(t -> { t.setMobileNumber(mobileNumber);
    	t.setMobileOtp(otp);
    	userRepository.save(t);
    	});
    }
}
