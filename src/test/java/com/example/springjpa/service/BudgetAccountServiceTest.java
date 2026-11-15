package com.example.springjpa.service;

import com.example.springjpa.domain.BudgetUser;
import com.example.springjpa.repository.BudgetUserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BudgetAccountServiceTest {

    @Test
    void onlyConfiguredLegacyEmailCanClaimMigratedOwner() {
        BudgetUserRepository repository = mock(BudgetUserRepository.class);
        BudgetUser legacyOwner = new BudgetUser(null, null);
        when(repository.findByGoogleSubject("legacy-subject")).thenReturn(Optional.empty());
        when(repository.findById(1L)).thenReturn(Optional.of(legacyOwner));
        BudgetAccountService service = new BudgetAccountService(repository, "Original@Example.com");

        BudgetUser claimedUser = service.registerOrUpdateGoogleIdentity(
                "legacy-subject", "original@example.com");

        assertSame(legacyOwner, claimedUser);
        assertEquals("legacy-subject", claimedUser.getGoogleSubject());
        assertEquals("original@example.com", claimedUser.getEmail());
        verify(repository, never()).save(any(BudgetUser.class));
    }

    @Test
    void aDifferentVerifiedEmailGetsANewAccountWithoutClaimingLegacyData() {
        BudgetUserRepository repository = mock(BudgetUserRepository.class);
        BudgetUser legacyOwner = new BudgetUser(null, null);
        when(repository.findByGoogleSubject("new-subject")).thenReturn(Optional.empty());
        when(repository.findById(1L)).thenReturn(Optional.of(legacyOwner));
        when(repository.save(any(BudgetUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BudgetAccountService service = new BudgetAccountService(repository, "original@example.com");

        BudgetUser newUser = service.registerOrUpdateGoogleIdentity(
                "new-subject", "new@example.com");

        assertEquals("new-subject", newUser.getGoogleSubject());
        assertEquals("new@example.com", newUser.getEmail());
        assertNull(legacyOwner.getGoogleSubject());
        verify(repository).save(any(BudgetUser.class));
    }
}
