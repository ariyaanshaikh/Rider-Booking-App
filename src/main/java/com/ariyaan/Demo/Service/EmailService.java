package com.ariyaan.Demo.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties.Simple;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

	private final JavaMailSender mailSender;

	public EmailService(JavaMailSender mailSender) {
		this.mailSender = mailSender;
	}
	
	@Async
	public void send(String sendTo, String token) {
		try {
			System.out.println("Email is getting send");
			SimpleMailMessage message = new SimpleMailMessage();
			message.setTo(sendTo);
			message.setFrom("ariyaanshaikh1401@gmail.com");
			message.setSubject("Confirm your Email");
			String messageBody = """
					
					Thank you for registering. 
					
					Your OTP for Verification is %s
					
					Please confirm your email.
					
					http://localhost:8088/register/confirmToken?token=%s
					
					
					""".formatted(token,token);
			message.setText(messageBody);
			mailSender.send(message);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	// from mobile otp verification code pasted
	/*
	@Autowired
    private JavaMailSender mailSender;

    public void sendOtp(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Your OTP Code");
        message.setText("Your OTP code is: " + otp);
        mailSender.send(message);
    } 
}  */
	
	
}
