package com.example.springjpa.service;

import com.example.springjpa.domain.BudgetUser;
import com.example.springjpa.repository.BudgetUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BudgetAccountService {

    private static final long LEGACY_USER_ID = 1L;

    private final BudgetUserRepository userRepository;
    private final String legacyOwnerEmail;

    public BudgetAccountService(
            BudgetUserRepository userRepository,
            @Value("${budget.auth.legacy-owner-email:}") String legacyOwnerEmail) {
        this.userRepository = userRepository;
        this.legacyOwnerEmail = legacyOwnerEmail;
    }

    @Transactional
    public BudgetUser registerOrUpdateGoogleIdentity(String googleSubject, String email) {
        BudgetUser existingUser = userRepository.findByGoogleSubject(googleSubject).orElse(null);
        if (existingUser != null) {
            if (!email.equals(existingUser.getEmail())) {
                existingUser.updateEmail(email);
            }
            return existingUser;
        }

        BudgetUser legacyUser = userRepository.findById(LEGACY_USER_ID).orElse(null);
        if (legacyUser != null && legacyUser.getGoogleSubject() == null
                && matchesLegacyOwner(email)) {
            legacyUser.claimGoogleIdentity(googleSubject, email);
            return legacyUser;
        }

        return userRepository.save(new BudgetUser(googleSubject, email));
    }

    @Transactional(readOnly = true)
    public long requireUserId(String googleSubject) {
        return userRepository.findByGoogleSubject(googleSubject)
                .map(BudgetUser::getId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Sign in again to initialize your private budget."));
    }

    private boolean matchesLegacyOwner(String email) {
        return email != null && !email.isBlank() && !legacyOwnerEmail.isBlank()
                && email.equalsIgnoreCase(legacyOwnerEmail.trim());
    }
}
