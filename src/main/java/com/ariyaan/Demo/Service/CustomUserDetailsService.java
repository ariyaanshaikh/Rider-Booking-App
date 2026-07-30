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
	
	public CustomUserDetailsService(UserRepository userRepository,PasswordEncoder passwordEncoder,TokenService tokenService,EmailService emailService,
			OtpService otpService,JwtUtils jwtUtils) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.emailService = emailService;
		this.otpService = otpService;
		this.jwtUtils = jwtUtils;
	}


	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return userRepository.findByUserName(username)
				.orElseThrow(() -> new UsernameNotFoundException(username));
	}


	public void registerUser(User user) {
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

	    // 2. Encrypt the password
	    user.setPassword(passwordEncoder.encode(user.getPassword()));

	    // 3. Set the token data directly on the SAME 'user' object
	    // Assuming you added these fields to your User entity class
	//    user.setToken(UUID.randomUUID().toString());
	    user.setToken(otpService.generateOtp());
	    user.setCreatedAt(LocalDateTime.now());
	    user.setExpiresAt(LocalDateTime.now().plusMinutes(15));
	    user.setEnabled(false); // Make sure they are disabled until they confirm

	    // 4. SAVE ONLY ONCE
	    // This will create ONE row in the 'users' table with all columns filled.
	    userRepository.save(user);
	    
	    // 5. Send Email logic here...
		System.out.println("Confirmation token is >>> " + user.getToken());
		
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
	
	public Map<String,String> response(String token){
		
		User user = userRepository.findByToken(token)
	            .orElseThrow(() -> new IllegalStateException("Token not found"));
		
		 Map<String, String> response = new HashMap<>();
		  String jwttoken = jwtUtils.generateToken(user.getUserName()); 
	        
	        response.put("message", "OTP verified Successfully");
	        response.put("token", jwttoken);
	        return response;
	}
	
	public boolean verifyEmailAddress(String emailAddress) {
		if (emailAddress == null || emailAddress.isBlank()) {
			return false;
		}

		Optional<User> existingUser = userRepository.findByEmailIgnoreCase(emailAddress.trim());
		if(existingUser.isPresent()) {
			User user = existingUser.get();
			user.setToken(otpService.generateOtp());
			user.setCreatedAt(LocalDateTime.now());
			user.setConfirmedAt(null);
			user.setExpiresAt(LocalDateTime.now().plusMinutes(15));
			user.setEnabled(false);
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
