package com.example.springjpa.security;

import com.example.springjpa.service.BudgetAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "budget.auth.enabled=true",
        "budget.auth.google.client-id=",
        "budget.auth.google.client-secret=",
        "budget.auth.legacy-owner-email="
})
@AutoConfigureMockMvc
class BudgetSecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Autowired
    private BudgetAccountService accountService;

    @Test
    void categorizesUnauthorizedGoogleAccountCallbackFailure() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                new OAuth2Error("unauthorized_google_account"));

        BudgetSecurityConfiguration.googleAuthenticationFailureHandler()
                .onAuthenticationFailure(request, response, exception);

        assertEquals("/login.html?error=unauthorized_account", response.getRedirectedUrl());
    }

    @Test
    void redirectsUnauthenticatedBudgetRequestsToSetupLoginPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login.html?setup=required"));
    }

    @Test
    void allowsLoginPageAndReportsMissingGoogleConfiguration() throws Exception {
        mockMvc.perform(get("/login.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleConfigured").value(false))
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.csrfToken").isNotEmpty());
    }

    @Test
    void startsGoogleLoginWithTheConfiguredCallbackRoute() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("accounts.google.com/o/oauth2/v2/auth")))
                .andExpect(header().string("Location",
                        containsString("redirect_uri=http://localhost/login/oauth2/code/google")))
                .andExpect(header().string("Location", containsString("prompt=select_account")));
        assertEquals("14d", environment.getProperty("server.servlet.session.cookie.max-age"));
        assertEquals("14d", environment.getProperty("server.servlet.session.timeout"));
    }

    @Test
    void doesNotAllowUnauthenticatedAccessToBudgetApi() throws Exception {
        mockMvc.perform(get("/api/budget"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login.html?setup=required"));
    }

    @Test
    void requiresCsrfForBudgetChanges() throws Exception {
        String setupRequest = """
                {"startDate":"2026-10-30"}
                """;

        mockMvc.perform(post("/api/budget/setup")
                        .with(oidcLogin().idToken(token -> token.subject("csrf-test-subject")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setupRequest))
                .andExpect(status().isForbidden());

        accountService.registerOrUpdateGoogleIdentity("csrf-test-subject", "csrf-test@example.com");
        mockMvc.perform(post("/api/budget/setup")
                        .with(oidcLogin().idToken(token -> token.subject("csrf-test-subject")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setupRequest))
                .andExpect(status().isCreated());
    }
}
