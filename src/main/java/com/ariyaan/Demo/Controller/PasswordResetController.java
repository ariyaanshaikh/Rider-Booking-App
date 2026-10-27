package com.ariyaan.Demo.Controller;

import com.ariyaan.Demo.Service.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class PasswordResetController {
    private final PasswordResetService resets;
    public PasswordResetController(PasswordResetService resets) { this.resets=resets; }
    public record Send(@NotBlank @Email @Size(max=255) String email) {}
    public record Verify(@NotBlank @Email @Size(max=255) String email,@NotBlank @Pattern(regexp="[0-9]{6}") String otp) {}
    public record Finish(@NotBlank @Email @Size(max=255) String email,@NotBlank @Size(max=100) String resetToken,
            @NotBlank @Size(min=8,max=72) String password) {}
    @PostMapping("/sendotp")
    public Map<String,Object> send(@Valid @RequestBody Send input) {
        resets.request(input.email());
        return Map.of("success",true,"message","If this email belongs to an active account, a reset code will be sent");
    }
    @PostMapping("/verifyotp")
    public Map<String,Object> verify(@Valid @RequestBody Verify input) { return resets.verify(input.email(),input.otp()); }
    @PostMapping("/resetpassword")
    public Map<String,Object> finish(@Valid @RequestBody Finish input) {
        resets.reset(input.email(),input.resetToken(),input.password());
        return Map.of("success",true,"message","Password changed. Sign in again on your devices.");
    }
}
