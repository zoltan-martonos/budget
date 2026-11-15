package com.example.springjpa.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistantQuestionRequest(
        @NotBlank @Size(max = 2_000) String question
) {
}
