package com.ariyaan.Demo.exception;

/**
 * Exception thrown when a ride with given ID is not found.
 */
public class RideNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RideNotFoundException(String message) {
		super(message);
	}

	public RideNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}
}
