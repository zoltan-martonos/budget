package com.example.springjpa.api.dto;

import java.time.LocalDate;

public record ExpenseResponse(
        Long id,
        LocalDate spentOn,
        long amountCents,
        String description
) {
}
