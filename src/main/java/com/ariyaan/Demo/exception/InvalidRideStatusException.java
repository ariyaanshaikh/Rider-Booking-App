package com.ariyaan.Demo.exception;

/**
 * Exception thrown when ride operation fails due to invalid ride status.
 */
public class InvalidRideStatusException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InvalidRideStatusException(String message) {
		super(message);
	}

	public InvalidRideStatusException(String message, Throwable cause) {
		super(message, cause);
	}
}
