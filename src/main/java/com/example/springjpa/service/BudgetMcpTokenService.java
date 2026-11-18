package com.example.springjpa.service;

import com.example.springjpa.domain.BudgetMcpToken;
import com.example.springjpa.repository.BudgetMcpTokenRepository;
import com.example.springjpa.security.BudgetMcpPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class BudgetMcpTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BudgetAccountService accountService;
    private final BudgetMcpTokenRepository tokenRepository;

    public BudgetMcpTokenService(
            BudgetAccountService accountService,
            BudgetMcpTokenRepository tokenRepository) {
        this.accountService = accountService;
        this.tokenRepository = tokenRepository;
    }

    @Transactional
    public String rotateForGoogleSubject(String googleSubject) {
        long userId = accountService.requireUserId(googleSubject);
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = hash(token);

        BudgetMcpToken storedToken = tokenRepository.findById(userId)
                .orElseGet(() -> new BudgetMcpToken(userId, tokenHash));
        storedToken.rotate(tokenHash);
        tokenRepository.save(storedToken);
        return token;
    }

    @Transactional
    public void revokeForGoogleSubject(String googleSubject) {
        tokenRepository.deleteById(accountService.requireUserId(googleSubject));
    }

    @Transactional(readOnly = true)
    public Optional<BudgetMcpPrincipal> authenticate(String token) {
        return tokenRepository.findByTokenHash(hash(token))
                .map(storedToken -> new BudgetMcpPrincipal(storedToken.getUserId()));
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for MCP token authentication.", exception);
        }
    }
}
