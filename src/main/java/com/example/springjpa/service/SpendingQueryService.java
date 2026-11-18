package com.example.springjpa.service;

import com.example.springjpa.domain.BudgetSettings;
import com.example.springjpa.repository.BudgetSettingsRepository;
import com.example.springjpa.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class SpendingQueryService {

    private final BudgetSettingsRepository settingsRepository;
    private final ExpenseRepository expenseRepository;

    public SpendingQueryService(
            BudgetSettingsRepository settingsRepository,
            ExpenseRepository expenseRepository) {
        this.settingsRepository = settingsRepository;
        this.expenseRepository = expenseRepository;
    }

    @Transactional(readOnly = true)
    public long totalSpentCents(long userId, LocalDate startDate, LocalDate endDate) {
        requireDate(startDate, "Start date");
        requireDate(endDate, "End date");
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be on or before end date.");
        }
        requireNotFuture(endDate);

        BudgetSettings settings = requireBudget(userId);
        LocalDate effectiveStart = startDate.isBefore(settings.getAllowanceStartDate())
                ? settings.getAllowanceStartDate() : startDate;
        if (effectiveStart.isAfter(endDate)) {
            return 0;
        }
        return expenseRepository.sumAmountCentsByUserIdAndSpentOnBetween(
                userId, effectiveStart, endDate);
    }

    @Transactional(readOnly = true)
    public SpendingPeriodTotal totalSpentSinceBudgetStart(long userId) {
        BudgetSettings settings = requireBudget(userId);
        LocalDate startDate = settings.getAllowanceStartDate();
        LocalDate endDate = LocalDate.now();
        long totalSpentCents = totalSpentCents(userId, startDate, endDate);
        return new SpendingPeriodTotal(startDate, endDate, totalSpentCents);
    }

    @Transactional(readOnly = true)
    public SpendingPeriodTotal totalSpentForRecentWeeks(long userId, int weeks) {
        if (weeks < 1 || weeks > 52) {
            throw new IllegalArgumentException("Weeks must be between 1 and 52.");
        }

        BudgetSettings settings = requireBudget(userId);
        LocalDate endDate = LocalDate.now();
        long days = Math.multiplyExact((long) weeks, 7);
        LocalDate requestedStart = endDate.minusDays(days - 1);
        LocalDate effectiveStart = requestedStart.isBefore(settings.getAllowanceStartDate())
                ? settings.getAllowanceStartDate() : requestedStart;
        long totalSpentCents = effectiveStart.isAfter(endDate)
                ? 0
                : expenseRepository.sumAmountCentsByUserIdAndSpentOnBetween(
                        userId, effectiveStart, endDate);
        return new SpendingPeriodTotal(effectiveStart, endDate, totalSpentCents);
    }

    @Transactional(readOnly = true)
    public List<SpendingDateTotal> highestSpendingDates(long userId) {
        BudgetSettings settings = requireBudget(userId);
        LocalDate startDate = settings.getAllowanceStartDate();
        LocalDate endDate = LocalDate.now();
        if (startDate.isAfter(endDate)) {
            return List.of();
        }

        List<ExpenseRepository.DailySpendingTotal> dailyTotals =
                expenseRepository.findDailySpendingTotals(userId, startDate, endDate);
        if (dailyTotals.isEmpty()) {
            return List.of();
        }

        long maxSpentCents = dailyTotals.stream()
                .mapToLong(ExpenseRepository.DailySpendingTotal::getTotalSpentCents)
                .max()
                .orElseThrow();
        List<SpendingDateTotal> highestTotals = new ArrayList<>();
        for (ExpenseRepository.DailySpendingTotal dailyTotal : dailyTotals) {
            if (dailyTotal.getTotalSpentCents() == maxSpentCents) {
                highestTotals.add(new SpendingDateTotal(
                        dailyTotal.getSpentOn(), dailyTotal.getTotalSpentCents()));
            }
        }
        return List.copyOf(highestTotals);
    }

    @Transactional(readOnly = true)
    public long budgetBalanceCents(long userId, LocalDate asOfDate) {
        requireDate(asOfDate, "Date");
        requireNotFuture(asOfDate);

        BudgetSettings settings = requireBudget(userId);
        LocalDate allowanceStart = settings.getAllowanceStartDate();
        if (asOfDate.isBefore(allowanceStart)) {
            return 0;
        }

        long allowanceCents = Math.multiplyExact(
                BudgetService.DAILY_ALLOWANCE_CENTS,
                asOfDate.toEpochDay() - allowanceStart.toEpochDay() + 1);
        long spentCents = expenseRepository.sumAmountCentsByUserIdAndSpentOnBetween(
                userId, allowanceStart, asOfDate);
        return Math.subtractExact(allowanceCents, spentCents);
    }

    private BudgetSettings requireBudget(long userId) {
        return settingsRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Set up your budget before querying spending."));
    }

    private void requireDate(LocalDate date, String name) {
        if (date == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
    }

    private void requireNotFuture(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("The query date cannot be in the future.");
        }
    }

    public record SpendingPeriodTotal(LocalDate startDate, LocalDate endDate, long totalSpentCents) {
    }

    public record SpendingDateTotal(LocalDate date, long totalSpentCents) {
    }
}
