package com.ariyaan.Demo.Controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ariyaan.Demo.Entity.LoginRequest;
import com.ariyaan.Demo.Service.EmailService;
import com.ariyaan.Demo.Service.OtpService;
import com.ariyaan.Demo.Service.RiderDemoService;
import com.ariyaan.Demo.Service.SmsService;
import com.ariyaan.Demo.Service.UserDetials;
import com.ariyaan.Demo.config.JwtUtils;

import org.springframework.security.core.Authentication;

@Controller
public class DemoController {
	
	@Autowired
	private RiderDemoService demoService;
	
	@Autowired
	private AuthenticationManager authenticationManager;
	@Autowired
	private com.ariyaan.Demo.Service.RequestLimits requestLimits;
	
	@GetMapping("/demo")
//	@ResponseBody
	public String sendString() {
		
		demoService.sendRiderDetails();
		return "home";
	}
	
	@PostMapping("/login")
	@ResponseBody
	public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
		requestLimits.check("login", loginRequest.getUsername(), 20);
	    // 1. This line checks the username and password against the DB automatically
	    Authentication authentication = authenticationManager.authenticate(
	        new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
	    );

	    // 2. If we reach here, authentication was successful!
	    String jwt = jwtUtils.generateToken(loginRequest.getUsername());
	    return ResponseEntity.ok(Map.of("token", jwt));
	}
	
	@ResponseBody
	@GetMapping("/sendJWT")
	public Map<String,String> sendJWT(@RequestParam String username){
		
		 Map<String, String> response = new HashMap<>();
		String token = jwtUtils.generateToken(username); 
        
        response.put("message", "JWT Token has been generated successfully on the basis of Username");
        response.put("token", token);
        return response;
	}
	
	
	// for otp verification
	
	@Autowired
	private OtpService otpService;
	
	@Autowired
	private EmailService emailService;
	
	@Autowired
	private SmsService smsService;
	
	@Autowired
	private JwtUtils jwtUtils;
	
	
	@PostMapping("/otp/send/sms")
	@ResponseBody
	public String sendOtpSms(@RequestParam String phone,@RequestParam String userName) {
		String otp = otpService.generateOtp(phone);
		smsService.sendOtp(phone,otp);
		otpService.saveMobileNumber(phone, otp, userName);
		return "OTP sent to your phone Number. Please check your phone";
	}
	/*
	 
	 // commenting this since we will now be using JWT token and will send it once user verifies the OTP for mobile
	 
	@PostMapping("/otp/verify")
	public String verifyOtp(@RequestParam String key, @RequestParam String otp) {
		return otpService.validateOtp(key,otp) ? "OTP verified Successfully" : "Verification Failed";
	}
	*/
	
	
	@PostMapping("/otp/verify")
	@ResponseBody
	public Map<String, String> verifyOtp(@RequestParam String key, @RequestParam String otp,@RequestParam String username) {
	    Map<String, String> response = new HashMap<>();
	    
	    if (otpService.validateOtp(key, otp)) {
	        // Generate token using the phone number or username as the subject
	        String token = jwtUtils.generateToken(username); 
	        
	        response.put("message", "OTP verified Successfully");
	        response.put("token", token);
	        return response;
	    }
	    
	    response.put("message", "Verification Failed");
	    return response;
	}
	
	
	// anm end point to update user details
	
	
	
	
	// You should add a simple endpoint that the mobile app hits when it starts up. 
	// This verifies the token is still valid before showing the home screen.
	@GetMapping("/api/auth/validate")
	@ResponseBody
	public ResponseEntity<?> validateToken() {
	    // Get the username of the person who is currently authenticated
	    String username = org.springframework.security.core.context.SecurityContextHolder
	                        .getContext().getAuthentication().getName();
	    
	    return ResponseEntity.ok(Map.of(
	        "valid", true,
	        "username", username,
	        "message", "Welcome back!"
	    ));
	}
}

