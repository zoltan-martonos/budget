package com.example.springjpa.api.dto;

import java.time.LocalDate;

public record DailyBudgetResponse(
        LocalDate date,
        long spentCents,
        long balanceCents
) {
}
