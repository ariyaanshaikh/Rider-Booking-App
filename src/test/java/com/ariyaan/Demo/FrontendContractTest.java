package com.ariyaan.Demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import com.ariyaan.Demo.Entity.*;
import com.ariyaan.Demo.Repository.UserRepository;
import com.ariyaan.Demo.Service.*;
import com.ariyaan.Demo.config.JwtUtils;

@SpringBootTest
@AutoConfigureMockMvc
class FrontendContractTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtUtils jwt;
    @MockitoBean RequestLimits limits;
    @MockitoBean EmailService email;
    @MockitoBean DispatchExpiry expiry;
    @BeforeEach void cleanup() { users.deleteAll(); }
    User account(String name,UserRole role,boolean enabled) {
        var u=new User();u.setUserName(name);u.setEmail(name+"@example.com");u.setRole(role);u.setEnabled(enabled);
        u.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password123"));
        u.setToken("123456");u.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(15));
        return users.save(u);
    }
    @Test void registrationAcceptsBothFrontendRolesButIgnoresPersistenceAndConfirmationFields() throws Exception {
        mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"firstName":"Alice","lastName":"Rider","userName":"alice","password":"password123","email":"alice@example.com","role":"RIDER","id":999,"enabled":true,"confirmedAt":"2026-01-01T00:00:00","token":"attack"}
            """)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        var user=users.findByUserName("alice").orElseThrow();
        assertNotEquals(999,user.getId());assertFalse(user.isEnabled());assertNull(user.getConfirmedAt());
        assertTrue(user.getToken().matches("[0-9]{6}"));assertNotEquals("attack",user.getToken());
    }
    @Test void otpRemainsReadableButCanOnlyConfirmOnceAndResendDoesNotDisableAccount() throws Exception {
        account("alice",UserRole.RIDER,false);
        String body="{\"email\":\"alice@example.com\",\"otp\":\"123456\"}";
        mvc.perform(post("/register/confirmToken").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString()).andExpect(jsonPath("$.userId").isNumber());
        assertEquals("123456",users.findByUserName("alice").orElseThrow().getToken());
        mvc.perform(post("/register/confirmToken").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/register/verifyEmail").contentType(MediaType.APPLICATION_JSON).content("{\"emailAddress\":\"alice@example.com\"}"))
                .andExpect(status().isOk());
        assertTrue(users.findByUserName("alice").orElseThrow().isEnabled());
    }
    @Test void loginWorksWithoutJwtAndWrongPasswordIs401() throws Exception {
        account("payer",UserRole.ADMIN,true);
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"payer\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"payer\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void customerCannotChangeKycOrUseOldPasswordBypass() throws Exception {
        account("payer",UserRole.ADMIN,true);String bearer="Bearer "+jwt.generateToken("payer");
        mvc.perform(patch("/rider/9/verification").param("verified","true").header("Authorization",bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/rider/9/verification-details").header("Authorization",bearer)).andExpect(status().isForbidden());
        mvc.perform(post("/register/updateUser").param("userName","payer").param("password","hacked"))
                .andExpect(status().is4xxClientError());
    }
    @Test void readinessAndCheckoutRequireCorrectAuthenticationMechanism() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/payments/checkout/status")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/payments/checkout/status").header("X-Checkout-Token","invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/sendJWT").param("username","payer")).andExpect(status().is4xxClientError());
    }
}
