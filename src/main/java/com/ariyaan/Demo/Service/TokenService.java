package com.ariyaan.Demo.Service;

import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class TokenService {

	private final TokenRepository tokenRepository;
	public TokenService(TokenRepository tokenRepository) {
		this.tokenRepository = tokenRepository;
	}
	
	public Optional<Token> findByToken(String token){
		return tokenRepository.findByToken(token);
	}
	
	public Token save(Token token) {
		return tokenRepository.save(token);
	}
	
}
