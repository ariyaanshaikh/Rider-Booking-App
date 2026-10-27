package com.ariyaan.Demo.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ariyaan.Demo.Entity.User;
import com.ariyaan.Demo.Entity.UserRole;
import com.ariyaan.Demo.Repository.UserRepository;
import com.ariyaan.Demo.config.JwtUtils;

@Service
public class CustomUserDetailsService implements UserDetailsService{

	private final UserRepository userRepository;
	
	private final PasswordEncoder passwordEncoder;
	
	private final TokenService tokenService;
	
	private final EmailService emailService;
	
	private final OtpService otpService;
	
	private final JwtUtils jwtUtils;
	private final RequestLimits limits;
	
	public CustomUserDetailsService(UserRepository userRepository,PasswordEncoder passwordEncoder,TokenService tokenService,EmailService emailService,
			OtpService otpService,JwtUtils jwtUtils, RequestLimits limits) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.emailService = emailService;
		this.otpService = otpService;
		this.jwtUtils = jwtUtils;
		this.limits = limits;
	}


	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return userRepository.findByUserName(username)
				.orElseThrow(() -> new UsernameNotFoundException(username));
	}


	public void registerUser(User user) {
		limits.check("register", user.getEmail(), 5);
	/*	// check the db if the user with same email address
		// or username is already exists
		
		 userRepository.findByUserNameOrEmail(user.getUsername(), user.getEmail())
		 .ifPresent(existingUser -> {
			 	throw new IllegalStateException("User Already Exists");
		 }
		 );
		
		 // if user withthe username or email address does not exists
		 // then save the user
		 // first encrypt the password
		 String password = passwordEncoder.encode(user.getPassword());
		 user.setPassword(password);
		 userRepository.save(user);
		 
		 
		 // send an email with validation link
		 // user clicks the link to confirm
		 // after confirmation the user becomes enabled
		 
		 Token confirmationToken = new Token(UUID.randomUUID().toString(),LocalDateTime.now(),LocalDateTime.now().plusMinutes(15),user);
		tokenService.save(confirmationToken);
		
		// send email to user
		 */
		// 1. Validation
	    userRepository.findByUserNameOrEmail(user.getUsername(), user.getEmail())
	        .ifPresent(existingUser -> {
	            throw new IllegalStateException("User Already Exists");
	        });

	    if (user.getRole() == null) {
	    throw new IllegalArgumentException("role is required and must be ADMIN or RIDER");
	    }

	    // 2. Encrypt the password
	    user.setPassword(passwordEncoder.encode(user.getPassword()));

	    // 3. Set the token data directly on the SAME 'user' object
	    // Assuming you added these fields to your User entity class
	//    user.setToken(UUID.randomUUID().toString());
	    user.setToken(otpService.generateOtp());
	    user.setCreatedAt(LocalDateTime.now());
	    user.setExpiresAt(LocalDateTime.now().plusMinutes(15));
	    user.setEnabled(false); // Make sure they are disabled until they confirm
	    user.setId(0);
	    user.setConfirmedAt(null);

	    // 4. SAVE ONLY ONCE
	    // This will create ONE row in the 'users' table with all columns filled.
	    userRepository.save(user);
	    
	    // 5. Send Email logic here...
		
		emailService.send(user.getEmail(), user.getToken());
	}
	
	// added from tokens service from token  -  user
	public Optional<User> findByToken(String token){
		return userRepository.findByToken(token);
	}

	public boolean confirmToken(String token) {
		
		// 1. Find the USER who has this token
	    // Note: You need to add findByToken to your UserRepository
	    User user = userRepository.findByToken(token)
	            .orElseThrow(() -> new IllegalStateException("Token not found"));

	    // 2. Check if already confirmed
	    if (user.getConfirmedAt() != null) {
	        throw new IllegalStateException("Email already confirmed");
	    }

	    // 3. Check if token is expired
	    LocalDateTime expiresAt = user.getExpiresAt();
	    if (expiresAt.isBefore(LocalDateTime.now())) {
	        throw new IllegalStateException("Token expired");
	    }

	    // 4. Update confirmation time and enable the user
	    user.setConfirmedAt(LocalDateTime.now());
	    user.setEnabled(true);
	    
	    // 5. Save the updated user (same row gets updated)
	    userRepository.save(user);

//	    emailService.send(user.getEmail(), user.getToken());
	    
	   return true;
	 //   return "Email confirmed successfully!";
	}

	@org.springframework.transaction.annotation.Transactional
	public Map<String, Object> confirmEmailOtp(String email, String otp) {
		if (email == null || email.isBlank() || otp == null || otp.isBlank()) {
			throw new IllegalArgumentException("email and otp are required");
		}
		limits.check("email-otp", email, 10);

		User user = userRepository.findByEmailForUpdate(email.trim())
				.orElseThrow(() -> new IllegalStateException("Email or OTP is invalid"));

		if (!otp.trim().equals(user.getToken())) {
			throw new IllegalStateException("Email or OTP is invalid");
		}

		if (user.getConfirmedAt() != null) {
			throw new IllegalStateException("Email already confirmed");
		}

		LocalDateTime expiresAt = user.getExpiresAt();
		if (expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
			throw new IllegalStateException("OTP expired");
		}

		user.setConfirmedAt(LocalDateTime.now());
		user.setEnabled(true);
		userRepository.save(user);

		Map<String, Object> response = new HashMap<>();
		response.put("message", "OTP verified successfully");
		response.put("token", jwtUtils.generateToken(user.getUserName()));
		response.put("userId", user.getId());
		return response;
	}
	
	public Map<String, Object> response(String token){
		
		User user = userRepository.findByToken(token)
	            .orElseThrow(() -> new IllegalStateException("Token not found"));
		
		 Map<String, Object> response = new HashMap<>();
		  String jwttoken = jwtUtils.generateToken(user.getUserName()); 
        
	        response.put("message", "OTP verified Successfully");
	        response.put("token", jwttoken);
	        response.put("userId", user.getId());
	        return response;
	}
	
	@org.springframework.transaction.annotation.Transactional
	public boolean verifyEmailAddress(String emailAddress) {
		if (emailAddress == null || emailAddress.isBlank()) {
			return false;
		}
		limits.check("resend", emailAddress, 3);

		Optional<User> existingUser = userRepository.findByEmailForUpdate(emailAddress.trim());
		if(existingUser.isPresent()) {
			User user = existingUser.get();
			if (user.isEnabled()) return true; // Resend must never disable a working account.
			user.setToken(otpService.generateOtp());
			user.setCreatedAt(LocalDateTime.now());
			user.setExpiresAt(LocalDateTime.now().plusMinutes(15));
			userRepository.save(user);
			// to email the token
			emailService.send(user.getEmail(), user.getToken());
			return true;
		}
		return false;
	}
	


	private void enableUser(User user) {

		user.setEnabled(true);
		userRepository.save(user);
		
	}

}
