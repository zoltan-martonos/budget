package com.example.springjpa.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateExpenseRequest(
        @NotNull @Positive Long amountCents,
        @Size(max = 240) String description,
        @NotNull LocalDate spentOn
) {
}
