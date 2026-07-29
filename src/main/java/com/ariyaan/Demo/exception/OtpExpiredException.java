package com.ariyaan.Demo.exception;

/**
 * Exception thrown when OTP verification fails due to OTP expiry.
 */
public class OtpExpiredException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public OtpExpiredException(String message) {
		super(message);
	}

	public OtpExpiredException(String message, Throwable cause) {
		super(message, cause);
	}
}
