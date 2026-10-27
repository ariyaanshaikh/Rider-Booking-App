package com.ariyaan.Demo.Service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

	private final JavaMailSender mailSender;
    @org.springframework.beans.factory.annotation.Value("${app.mail.from:${spring.mail.username:noreply@localhost}}")
    private String sender;
    /** Synchronous so the durable worker can retry an SMTP failure. */
    public void sendReset(String recipient,String otp) {
        var message=new SimpleMailMessage();message.setTo(recipient);message.setFrom(sender);
        message.setSubject("Reset your RiderLink password");
        message.setText("Your password reset code is: "+otp+"\nIt expires in 15 minutes. If you did not request it, ignore this message.");
        mailSender.send(message);
    }

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

					Your OTP for email verification is: %s

					Enter this OTP in the application to confirm your email address.

					Do not share this OTP with anyone.
				""".formatted(token);
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
