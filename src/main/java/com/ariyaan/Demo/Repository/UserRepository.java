package com.ariyaan.Demo.Repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.ariyaan.Demo.Entity.User;
import com.ariyaan.Demo.Service.Token;

@Repository
public interface UserRepository  extends CrudRepository<User, Long>{

	Optional<User> findByUserName(String userName);
	
	Optional<User> findByUserNameOrEmail(String username, String email);
	
	// added from token
//	Optional<Token> findByToken(String token);
	
	Optional<User> findByToken(String token);
	
	Optional<User> findByTokenAndUserName(String token, String username);
	
	
	// just added now to use in future in case
	Optional<User> findByMobileNumber(String mobileNumber);
	
	Optional<User> findByEmailIgnoreCase(String email);
}
