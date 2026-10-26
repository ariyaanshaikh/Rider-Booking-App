package com.ariyaan.Demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties={"app.jwt.secret=context-test-signing-key-with-at-least-32-bytes",
    "spring.flyway.enabled=false", "razorpay.enabled=false", "razorpay.route-enabled=false"})
class PracticeApplicationTests {

	@Test
	void contextLoads() {
	}

}
