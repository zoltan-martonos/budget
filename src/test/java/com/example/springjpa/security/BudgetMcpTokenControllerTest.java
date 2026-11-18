package com.example.springjpa.security;

import com.example.springjpa.service.BudgetAccountService;
import com.example.springjpa.service.BudgetMcpTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "budget.auth.enabled=true",
        "budget.auth.google.client-id=",
        "budget.auth.google.client-secret=",
        "budget.auth.legacy-owner-email="
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class BudgetMcpTokenControllerTest {

    private static final String SUBJECT = "mcp-token-owner";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BudgetAccountService accountService;

    @Autowired
    private BudgetMcpTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private long userId;

    @BeforeEach
    void registerUser() {
        accountService.registerOrUpdateGoogleIdentity(SUBJECT, "mcp-token-owner@example.com");
        userId = accountService.requireUserId(SUBJECT);
    }

    @Test
    void issuesOnlyForOidcSessionWithCsrfStoresOnlyHashAndSupportsRotationAndRevocation(
            CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/mcp-token").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/api/mcp-token")
                        .with(oidcLogin().idToken(token -> token.subject(SUBJECT))))
                .andExpect(status().isForbidden());

        String firstToken = issueToken();
        assertTrue(firstToken.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(firstToken,
                jdbcTemplate.queryForObject(
                        "SELECT token_hash FROM mcp_access_tokens WHERE user_id = ?",
                        String.class, userId));
        assertEquals(hash(firstToken),
                jdbcTemplate.queryForObject(
                        "SELECT token_hash FROM mcp_access_tokens WHERE user_id = ?",
                        String.class, userId));
        assertTrue(tokenService.authenticate(firstToken).isPresent());

        String rotatedToken = issueToken();
        assertNotEquals(firstToken, rotatedToken);
        assertFalse(tokenService.authenticate(firstToken).isPresent());
        assertTrue(tokenService.authenticate(rotatedToken).isPresent());

        mockMvc.perform(delete("/api/mcp-token")
                        .with(oidcLogin().idToken(token -> token.subject(SUBJECT))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/mcp-token")
                        .with(oidcLogin().idToken(token -> token.subject(SUBJECT)))
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        assertFalse(tokenService.authenticate(rotatedToken).isPresent());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mcp_access_tokens WHERE user_id = ?", Integer.class, userId));
        assertFalse(output.getAll().contains(firstToken));
        assertFalse(output.getAll().contains(rotatedToken));
    }

    private String issueToken() throws Exception {
        var result = mockMvc.perform(post("/api/mcp-token")
                        .with(oidcLogin().idToken(token -> token.subject(SUBJECT)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private String hash(String token) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
