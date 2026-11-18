package com.example.springjpa.service;

import com.example.springjpa.api.dto.BudgetSetupRequest;
import com.example.springjpa.api.dto.CreateExpenseRequest;
import com.example.springjpa.repository.BudgetSettingsRepository;
import com.example.springjpa.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "budget.auth.enabled=true",
        "budget.auth.google.client-id=",
        "budget.auth.google.client-secret=",
        "budget.auth.legacy-owner-email="
})
@AutoConfigureMockMvc
class BudgetMcpServerTest {

    private static final String OWNER_SUBJECT = "mcp-owner-subject";
    private static final String OTHER_SUBJECT = "mcp-other-subject";
    private static final String MCP_ACCEPT = "application/json, text/event-stream";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BudgetAccountService accountService;

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private BudgetSettingsRepository settingsRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @BeforeEach
    void setUpBudgets() {
        expenseRepository.deleteAllInBatch();
        settingsRepository.deleteAllInBatch();
        accountService.registerOrUpdateGoogleIdentity(OWNER_SUBJECT, "mcp-owner@example.com");
        accountService.registerOrUpdateGoogleIdentity(OTHER_SUBJECT, "mcp-other@example.com");

        LocalDate today = LocalDate.now();
        budgetService.setup(accountService.requireUserId(OWNER_SUBJECT),
                new BudgetSetupRequest(today.minusDays(1)));
        budgetService.setup(accountService.requireUserId(OTHER_SUBJECT),
                new BudgetSetupRequest(today.minusDays(1)));
        budgetService.addExpense(accountService.requireUserId(OWNER_SUBJECT),
                new CreateExpenseRequest(1_250L, "Owner expense", today));
        budgetService.addExpense(accountService.requireUserId(OTHER_SUBJECT),
                new CreateExpenseRequest(9_000L, "Other expense", today));
    }

    @Test
    void rejectsUnauthenticatedMcpRequestsAndRequiresCsrfForSignedInRequests() throws Exception {
        String initialize = initializeRequest();
        mockMvc.perform(get("/mcp").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/mcp")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MCP_ACCEPT)
                        .content(initialize))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/mcp")
                        .with(oidcLogin().idToken(token -> token.subject(OWNER_SUBJECT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MCP_ACCEPT)
                        .content(initialize))
                .andExpect(status().isForbidden());

        initializeSession(initialize);
    }

    @Test
    void exposesOnlyReadOnlySpendingToolsAndScopesCallsToTheAuthenticatedUser() throws Exception {
        McpClientSession session = initializeSession(initializeRequest());

        mockMvc.perform(post("/mcp")
                        .with(csrf().asHeader())
                        .session(session.httpSession())
                        .header("Mcp-Session-Id", session.mcpSessionId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MCP_ACCEPT)
                        .content("""
                                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("daily_spending_total")))
                .andExpect(content().string(containsString("weekly_spending_total")))
                .andExpect(content().string(containsString("recent_spending_total")))
                .andExpect(content().string(containsString("spending_since_budget_start")))
                .andExpect(content().string(containsString("highest_spending_date")))
                .andExpect(content().string(not(containsString("add_expense"))))
                .andExpect(content().string(not(containsString("delete_expense"))));

        mockMvc.perform(post("/mcp")
                        .with(csrf().asHeader())
                        .session(session.httpSession())
                        .header("Mcp-Session-Id", session.mcpSessionId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MCP_ACCEPT)
                        .content("""
                                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
                                  "name":"daily_spending_total","arguments":{"date":"%s"}
                                }}
                                """.formatted(LocalDate.now())))
                .andExpect(status().isOk())
                                .andExpect(content().string(containsString("12.50")))
                .andExpect(content().string(not(containsString("90.00"))));
    }

    private McpClientSession initializeSession(String request) throws Exception {
        MvcResult result = mockMvc.perform(post("/mcp")
                        .with(oidcLogin().idToken(token -> token.subject(OWNER_SUBJECT)))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MCP_ACCEPT)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(header().exists("Mcp-Session-Id"))
                .andReturn();
        String sessionId = result.getResponse().getHeader("Mcp-Session-Id");
        MockHttpSession httpSession = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(sessionId);
        assertNotNull(httpSession);
        return new McpClientSession(sessionId, httpSession);
    }

    private String initializeRequest() {
        return """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-03-26","capabilities":{},
                  "clientInfo":{"name":"budget-mcp-test","version":"1.0"}
                }}
                """;
    }

    private record McpClientSession(String mcpSessionId, MockHttpSession httpSession) {
    }
}
