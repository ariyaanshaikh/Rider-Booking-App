package com.ariyaan.Demo.Controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ariyaan.Demo.Entity.User;

import com.ariyaan.Demo.Dto.EmailOtpConfirmationRequest;
import com.ariyaan.Demo.Service.CustomUserDetailsService;
import com.ariyaan.Demo.Service.UserDetials;
import com.ariyaan.Demo.config.JwtUtils;

//@RestController
@Controller
@RequestMapping("register")
public class AuthenticationController {

	private final CustomUserDetailsService userDetailsService;
	
	@Autowired
	private UserDetials	userDetails;
	
	@Autowired
	private CustomUserDetailsService customUserDetailsService;
	
	@Autowired
	private JwtUtils jwtUtils;
	
	public AuthenticationController(CustomUserDetailsService userDetailsService) {
		super();
		this.userDetailsService = userDetailsService;
	}

	@GetMapping
	public String register(Model model) {
		User user = new User();
		model.addAttribute("user",user);
		return "register";
	}
	/*
	@PostMapping
	public String postUser(@ModelAttribute("user")User user,Model model, RedirectAttributes redirectAttributes) {
	// save user to DB
		userDetailsService.registerUser(user);
		redirectAttributes.addFlashAttribute("message","Please confirm your email address");
		return "redirect:/register";
		
	}  */
	
	// this logic is for mazz's code
	@PostMapping
	public ResponseEntity<?> postUser(@jakarta.validation.Valid @RequestBody com.ariyaan.Demo.Dto.RegistrationRequest input) {

	    userDetailsService.registerUser(input.toUser());

	    return ResponseEntity.ok().body(
	        Map.of(
	            "success", true,
	            "message", "Please confirm your email address"
	        )
	    );
	}

	
	// Confirm the OTP entered in the UI. The OTP never appears in the URL.
	@PostMapping("/confirmToken")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> confirmToken(
			@RequestBody EmailOtpConfirmationRequest request) {
		try {
			return ResponseEntity.ok(userDetailsService.confirmEmailOtp(request.getEmail(), request.getOtp()));
		} catch (IllegalArgumentException | IllegalStateException exception) {
			return ResponseEntity.badRequest().body(Map.of(
					"success", false,
					"message", exception.getMessage()
			));
		}
	}
	
	
	// forgot password re-email verification api
	@GetMapping("/verifyEmail")
	@ResponseBody
	public ResponseEntity<?> verifyEmail(@RequestParam("emailAddress") String emailAddress) {
		boolean emailRequested = customUserDetailsService.verifyEmailAddress(emailAddress);

		return ResponseEntity.ok(Map.of(
				"success", true,
				"message", "If this address is registered, a confirmation email has been requested"
		));
	}
	
	
	
	@PostMapping("/updateUser")
	@ResponseBody
	public String updateUserPassword(@ModelAttribute("user")User user,Model model, RedirectAttributes redirectAttributes) {
	// save user to DB
		throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,
				"Password changes require a verified reset session");
		
	}

	@PostMapping("/verifyEmail")
	@ResponseBody
	public ResponseEntity<?> resend(@RequestBody Map<String,String> input) {
		return verifyEmail(input.get("emailAddress"));
	}
	
	
}
