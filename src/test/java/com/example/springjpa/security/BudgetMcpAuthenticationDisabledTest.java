package com.example.springjpa.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "budget.auth.enabled=false")
@AutoConfigureMockMvc
class BudgetMcpAuthenticationDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void doesNotAllowAnonymousMcpRequestsWhenWebAuthenticationIsDisabled() throws Exception {
        mockMvc.perform(get("/mcp"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(post("/mcp")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept("application/json, text/event-stream")
                        .content("""
                                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                                  "protocolVersion":"2025-03-26","capabilities":{},
                                  "clientInfo":{"name":"anonymous-test","version":"1.0"}
                                }}
                                """))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(post("/api/mcp-token").with(csrf()))
                .andExpect(status().is4xxClientError());
    }
}
