package vn.com.fis.consentcore.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AdminAuthController {
    private final AdminAuthService authService;
    private final Clock clock;

    AdminAuthController(AdminAuthService authService, Clock clock) {
        this.authService = authService;
        this.clock = clock;
    }

    @PostMapping("/login")
    AdminAuthService.TokenResponse login(@Valid @RequestBody PasswordRequest request) {
        return authService.login(request.username(), request.password(), clock.instant());
    }

    @PostMapping("/refresh")
    AdminAuthService.TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken(), clock.instant());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken(), clock.instant());
    }

    record PasswordRequest(@NotBlank String username, @NotBlank String password) {
    }

    record RefreshRequest(@NotBlank String refreshToken) {
    }
}
