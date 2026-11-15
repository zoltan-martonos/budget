package com.example.springjpa.api.dto;

import java.time.LocalDate;

public record WeeklySpendResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        long spentCents,
        long allowanceCents
) {
}
