package com.example.springjpa.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AssistantChatConfiguration {

    @Bean
    ChatClient assistantChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
