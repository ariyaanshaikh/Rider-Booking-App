package com.ariyaan.Demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/** Loads secret-free payment defaults even when local application.properties is gitignored. */
@Configuration
@PropertySource("classpath:payments.properties")
public class PaymentConfiguration {
}
