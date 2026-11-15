package com.example.springjpa.api.dto;

import java.time.LocalDate;
import java.util.List;

public record BudgetDashboardResponse(
        boolean setupRequired,
        LocalDate startDate,
        LocalDate today,
        String month,
        long dailyAllowanceCents,
        long currentBalanceCents,
        long totalSpentCents,
        long todaySpentCents,
        List<DailyBudgetResponse> dailySummaries,
        List<ExpenseResponse> expenses,
        List<WeeklySpendResponse> weeklySpending
) {
}
