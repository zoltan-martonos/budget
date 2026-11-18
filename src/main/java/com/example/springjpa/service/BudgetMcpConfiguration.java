package com.example.springjpa.service;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BudgetMcpConfiguration {

    @Bean
    ToolCallbackProvider budgetMcpToolCallbackProvider(BudgetSpendingTools spendingTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(spendingTools)
                .build();
    }
}
