package com.ariyaan.Demo.Controller;

import java.util.HashMap;
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
	public ResponseEntity<?> postUser(@RequestBody User user) {

	    userDetailsService.registerUser(user);

	    return ResponseEntity.ok().body(
	        Map.of(
	            "success", true,
	            "message", "Please confirm your email address"
	        )
	    );
	}

	
	// confirm token for email verification
	@GetMapping("/confirmToken")
	@ResponseBody
	public Map<String,String> confirmToken(@RequestParam("token") String token, Model model) {
		
		 Map<String, String> response = new HashMap<>();
		    if (userDetailsService.confirmToken(token)) {
		    	response = userDetailsService.response(token);
		    	return response;
		    }
		    response.put("message", "Verification Failed");
		    return response;
	//	return"confirmToken";
	}
	
	
	// forgot password re-email verification api
	@GetMapping("/verifyEmail")
	@ResponseBody
	public ResponseEntity<?> verifyEmail(@RequestParam("emailAddress") String emailAddress) {
		boolean emailRequested = customUserDetailsService.verifyEmailAddress(emailAddress);

		if (!emailRequested) {
			return ResponseEntity.status(404).body(Map.of(
					"success", false,
					"message", "No user was found for this email address"
			));
		}

		return ResponseEntity.ok(Map.of(
				"success", true,
				"message", "A confirmation email has been requested"
		));
	}
	
	
	
	@PostMapping("/updateUser")
	@ResponseBody
	public String updateUserPassword(@ModelAttribute("user")User user,Model model, RedirectAttributes redirectAttributes) {
	// save user to DB
		userDetails.registerUser(user);
		redirectAttributes.addFlashAttribute("message","Please confirm your email address");
		return "user updatedb successfully";
		
	}
	
	
}
