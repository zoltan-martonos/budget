package com.example.springjpa.api;

import com.example.springjpa.api.dto.AssistantQuestionRequest;
import com.example.springjpa.api.dto.AssistantResponse;
import com.example.springjpa.service.AssistantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/query")
    public AssistantResponse ask(
            @AuthenticationPrincipal OidcUser user,
            @Valid @RequestBody AssistantQuestionRequest request) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to ask budget questions.");
        }
        return new AssistantResponse(assistantService.answer(user, request.question()));
    }
}
