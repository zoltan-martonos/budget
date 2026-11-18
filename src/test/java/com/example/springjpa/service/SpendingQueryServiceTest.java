package com.example.springjpa.service;

import com.example.springjpa.api.dto.BudgetSetupRequest;
import com.example.springjpa.api.dto.CreateExpenseRequest;
import com.example.springjpa.repository.BudgetSettingsRepository;
import com.example.springjpa.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class SpendingQueryServiceTest {

    private static final String OWNER_SUBJECT = "spending-query-owner";
    private static final String OTHER_SUBJECT = "spending-query-other";

    @Autowired
    private SpendingQueryService spendingQueryService;

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private BudgetAccountService accountService;

    @Autowired
    private BudgetSettingsRepository settingsRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @BeforeEach
    void clearBudgets() {
        expenseRepository.deleteAllInBatch();
        settingsRepository.deleteAllInBatch();
        accountService.registerOrUpdateGoogleIdentity(OWNER_SUBJECT, "query-owner@example.com");
        accountService.registerOrUpdateGoogleIdentity(OTHER_SUBJECT, "query-other@example.com");
    }

    @Test
    void sumsOnlyTheAuthenticatedUsersExpensesAndCalculatesBalanceFromAllowanceStart() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(2);
        long ownerId = accountService.requireUserId(OWNER_SUBJECT);
        long otherId = accountService.requireUserId(OTHER_SUBJECT);
        budgetService.setup(ownerId, new BudgetSetupRequest(startDate));
        budgetService.setup(otherId, new BudgetSetupRequest(startDate));
        budgetService.addExpense(ownerId, new CreateExpenseRequest(1_000L, "start", startDate));
        budgetService.addExpense(ownerId, new CreateExpenseRequest(2_500L, "today", today));
        budgetService.addExpense(otherId, new CreateExpenseRequest(50_000L, "private", today));

        assertEquals(3_500, spendingQueryService.totalSpentCents(
                ownerId, startDate, today));
        assertEquals(1_000, spendingQueryService.totalSpentCents(
                ownerId, startDate.minusDays(10), startDate.plusDays(1)));
        assertEquals(5_500, spendingQueryService.budgetBalanceCents(
                ownerId, today));
        assertEquals(0, spendingQueryService.totalSpentCents(
                ownerId, startDate.minusDays(3), startDate.minusDays(1)));
    }

    @Test
    void requiresAnExistingBudgetAndRejectsInvalidOrFutureDates() {
        LocalDate today = LocalDate.now();
        long ownerId = accountService.requireUserId(OWNER_SUBJECT);
        budgetService.setup(ownerId, new BudgetSetupRequest(today));

        assertThrows(IllegalArgumentException.class, () ->
                spendingQueryService.totalSpentCents(ownerId, today, today.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () ->
                spendingQueryService.totalSpentCents(ownerId, today, today.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () ->
                spendingQueryService.budgetBalanceCents(ownerId, today.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () ->
                spendingQueryService.budgetBalanceCents(ownerId, null));

        accountService.registerOrUpdateGoogleIdentity(
                "spending-query-no-budget", "no-budget@example.com");
        long noBudgetUserId = accountService.requireUserId("spending-query-no-budget");
        assertThrows(IllegalStateException.class, () ->
                spendingQueryService.budgetBalanceCents(noBudgetUserId, today));
    }
}
