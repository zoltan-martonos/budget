package com.example.springjpa.security;

import com.example.springjpa.service.BudgetMcpTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class BudgetMcpBearerTokenFilter extends OncePerRequestFilter {

    private final BudgetMcpTokenService tokenService;

    public BudgetMcpBearerTokenFilter(BudgetMcpTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(
                true, 0, "Bearer ", 0, "Bearer ".length())) {
            reject(response);
            return;
        }

        String token = authorization.substring("Bearer ".length());
        if (token.isBlank() || token.chars().anyMatch(Character::isWhitespace)) {
            reject(response);
            return;
        }

        BudgetMcpPrincipal principal = tokenService.authenticate(token).orElse(null);
        if (principal == null) {
            reject(response);
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
