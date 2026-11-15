package com.example.springjpa.api;

import com.example.springjpa.domain.AssistantExchange;
import com.example.springjpa.repository.AssistantExchangeRepository;
import com.example.springjpa.service.BudgetAccountService;
import com.example.springjpa.service.BudgetSpendingTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BudgetSpendingTools spendingTools;

    @Autowired
    private BudgetAccountService accountService;

    @Autowired
    private AssistantExchangeRepository exchangeRepository;

    @MockitoBean
    private ChatClient chatClient;

    private ChatClient.ChatClientRequestSpec prompt;

    @BeforeEach
    void setUpChatClient() {
        accountService.registerOrUpdateGoogleIdentity(
                "assistant-controller-owner", "assistant-controller-owner@example.com");
        long userId = accountService.requireUserId("assistant-controller-owner");
        exchangeRepository.deleteAll(exchangeRepository.findAllByUserIdOrderByCreatedAtAsc(userId));
        prompt = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(prompt);
        when(prompt.system(anyString())).thenReturn(prompt);
        when(prompt.user(anyString())).thenReturn(prompt);
        when(prompt.tools(spendingTools)).thenReturn(prompt);
        when(prompt.call()).thenReturn(response);
        when(response.content()).thenReturn("You spent €12.50 yesterday.");
    }

    @Test
    void sendsQuestionToChatClientWithScopedBudgetTools() throws Exception {
        mockMvc.perform(asOwner(post("/api/assistant/query"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"How much did I spend yesterday?","userId":987654}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("You spent €12.50 yesterday."));

        verify(prompt).tools(spendingTools);
        org.mockito.ArgumentCaptor<String> systemPrompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(prompt).system(systemPrompt.capture());
        assertTrue(systemPrompt.getValue().contains("recent_spending_total"));
        assertTrue(systemPrompt.getValue().contains("Never use"));
        assertTrue(systemPrompt.getValue().contains("bounded recent period"));
        assertTrue(systemPrompt.getValue().contains("highest_spending_date"));
        assertTrue(systemPrompt.getValue().contains("Never invent a date"));
        assertTrue(systemPrompt.getValue().contains("spending_since_budget_start"));
        assertTrue(systemPrompt.getValue().contains("since your budget started on DATE"));
        assertTrue(systemPrompt.getValue().contains("Never infer an account"));

        long userId = accountService.requireUserId("assistant-controller-owner");
        List<AssistantExchange> exchanges =
                exchangeRepository.findAllByUserIdOrderByCreatedAtAsc(userId);
        assertEquals(1, exchanges.size());
        assertEquals("How much did I spend yesterday?", exchanges.getFirst().getQuestion());
        assertEquals("You spent €12.50 yesterday.", exchanges.getFirst().getResponse());
        assertEquals(userId, exchanges.getFirst().getUserId());
    }

    @Test
    void doesNotPersistAnEmptyModelResponse() throws Exception {
        when(prompt.call().content()).thenReturn(" ");

        mockMvc.perform(asOwner(post("/api/assistant/query"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"A question with no answer"}
                                """))
                .andExpect(status().isConflict());

        long userId = accountService.requireUserId("assistant-controller-owner");
        assertTrue(exchangeRepository.findAllByUserIdOrderByCreatedAtAsc(userId).isEmpty());
    }

    @Test
    void validatesQuestionAndRejectsRequestsWithoutOidcAuthentication() throws Exception {
        mockMvc.perform(asOwner(post("/api/assistant/query"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":" "}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/assistant/query")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"How much did I spend yesterday?"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder asOwner(MockHttpServletRequestBuilder request) {
        return request.with(oidcLogin().idToken(idToken -> idToken.subject("assistant-controller-owner")))
                .with(csrf());
    }
}
