package com.example.springjpa.api;

import com.example.springjpa.service.BudgetMcpTokenService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class BudgetMcpTokenController {

    private final BudgetMcpTokenService tokenService;

    public BudgetMcpTokenController(BudgetMcpTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/api/mcp-token")
    public ResponseEntity<McpTokenResponse> rotate(@AuthenticationPrincipal OidcUser user) {
        String token = tokenService.rotateForGoogleSubject(googleSubject(user));
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(new McpTokenResponse(token));
    }

    @DeleteMapping("/api/mcp-token")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal OidcUser user) {
        tokenService.revokeForGoogleSubject(googleSubject(user));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private String googleSubject(OidcUser user) {
        if (user == null || user.getSubject() == null || user.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return user.getSubject();
    }

    public record McpTokenResponse(String token) {
    }
}
