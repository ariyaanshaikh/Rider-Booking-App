package com.ariyaan.Demo.exception;

/**
 * Exception thrown when an operation requires a rider to be assigned but rider is not.
 */
public class RiderNotAssignedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RiderNotAssignedException(String message) {
		super(message);
	}

	public RiderNotAssignedException(String message, Throwable cause) {
		super(message, cause);
	}
}
