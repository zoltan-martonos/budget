package com.example.springjpa.security;

import com.example.springjpa.service.BudgetAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleAuthorizationConfigurationTest {

    @Test
    void configuresGoogleJwkSetUriForIdTokenSignatureVerification() {
        ClientRegistrationRepository registrations =
                new BudgetSecurityConfiguration().clientRegistrationRepository("client-id", "client-secret");
        ClientRegistration google = registrations.findByRegistrationId("google");

        assertSame("https://www.googleapis.com/oauth2/v3/certs",
                google.getProviderDetails().getJwkSetUri());
    }

    @Test
    void acceptsAnyGoogleIdentityWithVerifiedEmailAndRegistersItBySubject() {
        OidcUserRequest request = mock(OidcUserRequest.class);
        OidcUser user = mockUser("different@example.com", "stable-google-sub", true);
        BudgetAccountService accountService = mock(BudgetAccountService.class);
        OAuth2UserService<OidcUserRequest, OidcUser> service =
                BudgetSecurityConfiguration.authorizeGoogleUser(ignored -> user, accountService);

        assertSame(user, service.loadUser(request));
        verify(accountService).registerOrUpdateGoogleIdentity("stable-google-sub", "different@example.com");
    }

    @Test
    void rejectsUnverifiedOrIncompleteGoogleIdentityWithoutCreatingAnAccount() {
        OidcUserRequest request = mock(OidcUserRequest.class);
        OidcUser unverifiedUser = mockUser("owner@example.com", "unverified-sub", false);
        OidcUser missingSubjectUser = mockUser("owner@example.com", "", true);
        BudgetAccountService accountService = mock(BudgetAccountService.class);

        OAuth2UserService<OidcUserRequest, OidcUser> unverifiedService =
                BudgetSecurityConfiguration.authorizeGoogleUser(ignored -> unverifiedUser, accountService);
        OAuth2UserService<OidcUserRequest, OidcUser> missingSubjectService =
                BudgetSecurityConfiguration.authorizeGoogleUser(ignored -> missingSubjectUser, accountService);

        assertThrows(OAuth2AuthenticationException.class, () -> unverifiedService.loadUser(request));
        assertThrows(OAuth2AuthenticationException.class, () -> missingSubjectService.loadUser(request));
        verify(accountService, never()).registerOrUpdateGoogleIdentity(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private static OidcUser mockUser(String email, String subject, boolean emailVerified) {
        OidcUser user = mock(OidcUser.class);
        when(user.getEmail()).thenReturn(email);
        when(user.getSubject()).thenReturn(subject);
        doReturn(emailVerified).when(user).getClaim("email_verified");
        return user;
    }
}
