package com.example.springjpa.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final boolean googleConfigured;

    public AuthController(
            @Value("${budget.auth.google.client-id:}") String clientId,
            @Value("${budget.auth.google.client-secret:}") String clientSecret) {
        this.googleConfigured = !clientId.isBlank() && !clientSecret.isBlank();
    }

    @GetMapping("/api/auth/status")
    public AuthStatus getStatus(Authentication authentication, CsrfToken csrfToken) {
        String email = authentication != null && authentication.getPrincipal() instanceof OidcUser user
                ? user.getEmail() : null;
        return new AuthStatus(googleConfigured, email != null, email, csrfToken.getToken());
    }

    public record AuthStatus(boolean googleConfigured, boolean authenticated, String email, String csrfToken) {
    }
}
