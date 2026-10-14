package com.wardk.meeteam_backend.web.auth.controller;

import com.wardk.meeteam_backend.global.auth.cookie.AccessTokenCookieProvider;
import com.wardk.meeteam_backend.global.auth.cookie.RefreshTokenCookieProvider;
import com.wardk.meeteam_backend.global.auth.service.TestAccountService;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("test & !prod & !production")
@ConditionalOnProperty(name = "test-account.enabled", havingValue = "true")
@RequestMapping("/api/v1/auth")
public class TestAccountController {
    private final TestAccountService accounts;
    private final AccessTokenCookieProvider accessCookies;
    private final RefreshTokenCookieProvider refreshCookies;

    public TestAccountController(TestAccountService accounts, AccessTokenCookieProvider accessCookies,
                                 RefreshTokenCookieProvider refreshCookies) {
        this.accounts = accounts;
        this.accessCookies = accessCookies;
        this.refreshCookies = refreshCookies;
    }

    @PostMapping("/login/test")
    public SuccessResponse<Void> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        var result = accounts.login(request.username(), request.password());
        accessCookies.addCookie(response, result.getAccessToken());
        refreshCookies.addCookie(response, result.getRefreshToken());
        return SuccessResponse.onSuccess(null);
    }

    public record LoginRequest(@NotBlank @Size(max = 64) String username,
                               @NotBlank @Size(max = 72) String password) {
        @Override
        public String toString() {
            return "LoginRequest[credentials redacted]";
        }
    }
}
