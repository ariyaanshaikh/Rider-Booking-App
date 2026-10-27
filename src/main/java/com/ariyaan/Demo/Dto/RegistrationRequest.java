package com.ariyaan.Demo.Dto;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.ariyaan.Demo.Entity.User;
import com.ariyaan.Demo.Entity.UserRole;

/** Only account input is accepted: persistence IDs and confirmation flags are ignored. */
public record RegistrationRequest(
        @NotBlank @Size(max=255) String firstName,
        @NotBlank @Size(max=255) String lastName,
        @JsonAlias("username") @NotBlank @Size(max=100) String userName,
        @NotBlank @Size(min=8,max=72) String password,
        @NotBlank @Email @Size(max=255) String email,
        @Size(max=50) String mobileNumber,
        @NotNull UserRole role) {
    public User toUser() {
        User user = new User();
        user.setFirstName(firstName.trim()); user.setLastName(lastName.trim());
        user.setUserName(userName.trim()); user.setPassword(password);
        user.setEmail(email.trim().toLowerCase(java.util.Locale.ROOT));
        user.setMobileNumber(mobileNumber); user.setRole(role);
        return user;
    }
}
