package com.ariyaan.Demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import com.ariyaan.Demo.Service.CustomUserDetailsService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private final CustomUserDetailsService customUserDetailsService;
	
	private final JwtFilter jwtFilter;
	
	
	public SecurityConfig(CustomUserDetailsService customUserDetailsService, JwtFilter jwtFilter) {
        this.customUserDetailsService = customUserDetailsService;
        this.jwtFilter = jwtFilter;
    }

/*//pasting this in to password Config class since it was causing cyclic dependency issue
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	} */
	
	
	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
	    return config.getAuthenticationManager();
	}
	
	
	@Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // CSRF is not needed for JWT/Mobile
            .authorizeHttpRequests(auth -> auth
                // SockJS performs its HTTP handshake before the STOMP CONNECT
                // frame can carry a JWT. The CONNECT frame itself is validated
                // by JwtChannelInterceptor.
                .requestMatchers("/sendJWT", "/otp/**", "/demo", "/register/updateUser").denyAll()
                .requestMatchers("/api/payments/checkout/**").permitAll()
                .requestMatchers("/register/**", "/login", "/health", "/health/live", "/error", "/ws/**", "/payment-checkout.html", "/api/payments/razorpay/webhook").permitAll()
                .anyRequest().authenticated()
            )
            // Tell Spring not to create sessions
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .userDetailsService(customUserDetailsService);

        return http.build();
    }
	
}
