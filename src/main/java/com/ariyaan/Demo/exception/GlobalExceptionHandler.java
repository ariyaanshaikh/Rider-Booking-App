package com.ariyaan.Demo.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler provides centralized exception handling for all controllers.
 * Ensures consistent error response format across the application.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
	@ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
	public ResponseEntity<Map<String,Object>> handleAuthentication(Exception ex) {
		return clientError(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Invalid credentials or account unavailable");
	}
	@ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
	public ResponseEntity<Map<String,Object>> handleConstraint(Exception ex) {
		return clientError(HttpStatus.CONFLICT,"CONFLICT","A record with these details already exists or is invalid");
	}
	@ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
		org.springframework.web.bind.ServletRequestBindingException.class})
	public ResponseEntity<Map<String,Object>> handleMalformedRequest(Exception ex) {
		return clientError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Required request body, header or parameter is missing or invalid");
	}
	@ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<Map<String,Object>> handleWrongMethod(Exception ex) {
		return clientError(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method is not supported for this endpoint");
	}
	@ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> handleResponseStatus(org.springframework.web.server.ResponseStatusException ex) {
		return ResponseEntity.status(ex.getStatusCode()).body(Map.of("status", ex.getStatusCode().value(),
			"error", "REQUEST_FAILED", "message", ex.getReason() == null ? "Request failed" : ex.getReason()));
	}

	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Map<String, Object>> handleAccessDeniedException(
			AccessDeniedException ex, WebRequest request) {
		Map<String, Object> body = new HashMap<>();
		body.put("timestamp", LocalDateTime.now());
		body.put("status", HttpStatus.FORBIDDEN.value());
		body.put("error", "ACCESS_DENIED");
		body.put("message", ex.getMessage());
		return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
	}

	/**
	 * Handle OTP expiry exceptions
	 */
	@ExceptionHandler(OtpExpiredException.class)
	public ResponseEntity<Map<String, Object>> handleOtpExpiredException(
			OtpExpiredException ex, WebRequest request) {

		logger.warn("OTP expired: {}", ex.getMessage());

		Map<String, Object> body = new HashMap<>();
		body.put("timestamp", LocalDateTime.now());
		body.put("status", HttpStatus.BAD_REQUEST.value());
		body.put("error", "OTP_EXPIRED");
		body.put("message", ex.getMessage());

		return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
	}

	/**
	 * Handle ride not found exceptions
	 */
	@ExceptionHandler(RideNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleRideNotFoundException(
			RideNotFoundException ex, WebRequest request) {

		logger.warn("Ride not found: {}", ex.getMessage());

		Map<String, Object> body = new HashMap<>();
		body.put("timestamp", LocalDateTime.now());
		body.put("status", HttpStatus.NOT_FOUND.value());
		body.put("error", "RIDE_NOT_FOUND");
		body.put("message", ex.getMessage());

		return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
	}

	/**
	 * Handle rider not assigned exceptions
	 */
	@ExceptionHandler(RiderNotAssignedException.class)
	public ResponseEntity<Map<String, Object>> handleRiderNotAssignedException(
			RiderNotAssignedException ex, WebRequest request) {

		logger.warn("Rider not assigned: {}", ex.getMessage());

		Map<String, Object> body = new HashMap<>();
		body.put("timestamp", LocalDateTime.now());
		body.put("status", HttpStatus.CONFLICT.value());
		body.put("error", "RIDER_NOT_ASSIGNED");
		body.put("message", ex.getMessage());

		return new ResponseEntity<>(body, HttpStatus.CONFLICT);
	}

	/**
	 * Handle invalid ride status exceptions
	 */
	@ExceptionHandler(InvalidRideStatusException.class)
	public ResponseEntity<Map<String, Object>> handleInvalidRideStatusException(
			InvalidRideStatusException ex, WebRequest request) {

		logger.warn("Invalid ride status: {}", ex.getMessage());

		Map<String, Object> body = new HashMap<>();
		body.put("timestamp", LocalDateTime.now());
		body.put("status", HttpStatus.BAD_REQUEST.value());
		body.put("error", "INVALID_RIDE_STATUS");
		body.put("message", ex.getMessage());

		return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
	}

	/**
	 * Handle validation errors (DTO validation failures)
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidationException(
			MethodArgumentNotValidException ex, WebRequest request) {

		logger.warn("Validation error: {}", ex.getMessage());

		Map<String, Object> errors = new HashMap<>();
		ex.getBindingResult().getAllErrors().forEach((error) -> {
			String fieldName = ((FieldError) error).getField();
			String errorMessage = error.getDefaultMessage();
			errors.put(fieldName, errorMessage);
		});

		Map<String, Object> body = new HashMap<>();
		body.put("timestamp", LocalDateTime.now());
		body.put("status", HttpStatus.BAD_REQUEST.value());
		body.put("error", "VALIDATION_FAILED");
		body.put("message", "Request validation failed");
		body.put("details", errors);

		return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
		return clientError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException ex) {
		return clientError(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
	}

	private ResponseEntity<Map<String, Object>> clientError(HttpStatus status, String error, String message) {
		Map<String, Object> body = new HashMap<>();
		body.put("timestamp", LocalDateTime.now());
		body.put("status", status.value());
		body.put("error", error);
		body.put("message", message);
		return new ResponseEntity<>(body, status);
	}

	/**
	 * Handle generic exceptions
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleGlobalException(
			Exception ex, WebRequest request) {

		logger.error("Unexpected error: {}", ex.getMessage(), ex);

		Map<String, Object> body = new HashMap<>();
		body.put("timestamp", LocalDateTime.now());
		body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
		body.put("error", "INTERNAL_SERVER_ERROR");
		body.put("message", "An unexpected error occurred");

		return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
	}
}
