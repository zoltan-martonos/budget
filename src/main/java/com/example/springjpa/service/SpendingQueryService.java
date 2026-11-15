package com.example.springjpa.service;

import com.example.springjpa.domain.BudgetSettings;
import com.example.springjpa.repository.BudgetSettingsRepository;
import com.example.springjpa.repository.ExpenseRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

@Service
public class SpendingQueryService {

    private final BudgetAccountService accountService;
    private final BudgetSettingsRepository settingsRepository;
    private final ExpenseRepository expenseRepository;

    public SpendingQueryService(
            BudgetAccountService accountService,
            BudgetSettingsRepository settingsRepository,
            ExpenseRepository expenseRepository) {
        this.accountService = accountService;
        this.settingsRepository = settingsRepository;
        this.expenseRepository = expenseRepository;
    }

    @Transactional(readOnly = true)
    public long totalSpentCents(OidcUser user, LocalDate startDate, LocalDate endDate) {
        requireDate(startDate, "Start date");
        requireDate(endDate, "End date");
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be on or before end date.");
        }
        requireNotFuture(endDate);

        long userId = userId(user);
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
    public SpendingPeriodTotal totalSpentSinceBudgetStart(OidcUser user) {
        long userId = userId(user);
        BudgetSettings settings = requireBudget(userId);
        LocalDate startDate = settings.getAllowanceStartDate();
        LocalDate endDate = LocalDate.now();
        long totalSpentCents = totalSpentCents(user, startDate, endDate);
        return new SpendingPeriodTotal(startDate, endDate, totalSpentCents);
    }

    @Transactional(readOnly = true)
    public long budgetBalanceCents(OidcUser user, LocalDate asOfDate) {
        requireDate(asOfDate, "Date");
        requireNotFuture(asOfDate);

        long userId = userId(user);
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

    private long userId(OidcUser user) {
        String subject = Objects.requireNonNull(user, "Authenticated user is required").getSubject();
        return accountService.requireUserId(subject);
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
}
