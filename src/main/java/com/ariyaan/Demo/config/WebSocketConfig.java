package com.ariyaan.Demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.ariyaan.Demo.config.JwtChannelInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final JwtChannelInterceptor jwtChannelInterceptor;

	public WebSocketConfig(JwtChannelInterceptor jwtChannelInterceptor) {
		this.jwtChannelInterceptor = jwtChannelInterceptor;
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		// Client subscribes to this topic
		registry.enableSimpleBroker("/topic", "/queue");
		// client sends message to /app
		registry.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws") // websocket end point connection
				.setAllowedOriginPatterns("*")
				.withSockJS();
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		// register interceptor to authenticate CONNECT frames using JWT
		registration.interceptors(jwtChannelInterceptor);
	}

}
