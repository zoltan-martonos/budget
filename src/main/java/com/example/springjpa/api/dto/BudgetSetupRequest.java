package com.example.springjpa.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

public record BudgetSetupRequest(
        @NotNull @PastOrPresent LocalDate startDate
) {
}
