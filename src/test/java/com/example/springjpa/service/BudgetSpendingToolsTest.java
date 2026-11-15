package com.example.springjpa.service;

import com.example.springjpa.api.dto.BudgetSetupRequest;
import com.example.springjpa.api.dto.CreateExpenseRequest;
import com.example.springjpa.repository.BudgetSettingsRepository;
import com.example.springjpa.repository.ExpenseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class BudgetSpendingToolsTest {

    private static final String OWNER_SUBJECT = "spending-tool-owner";
    private static final String OTHER_SUBJECT = "spending-tool-other";

    @Autowired
    private BudgetSpendingTools tools;

    @Autowired
    private BudgetAccountService accountService;

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private BudgetSettingsRepository settingsRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @BeforeEach
    void clearBudgetsAndAuthenticateOwner() {
        expenseRepository.deleteAllInBatch();
        settingsRepository.deleteAllInBatch();
        accountService.registerOrUpdateGoogleIdentity(OWNER_SUBJECT, "tool-owner@example.com");
        accountService.registerOrUpdateGoogleIdentity(OTHER_SUBJECT, "tool-other@example.com");
        authenticateAs(OWNER_SUBJECT);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dailyToolReturnsOnlyTheAuthenticatedUsersTotal() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(2);
        long ownerId = accountService.requireUserId(OWNER_SUBJECT);
        long otherId = accountService.requireUserId(OTHER_SUBJECT);
        budgetService.setup(ownerId, new BudgetSetupRequest(startDate));
        budgetService.setup(otherId, new BudgetSetupRequest(startDate));
        budgetService.addExpense(ownerId, new CreateExpenseRequest(1_250L, "Owner", today));
        budgetService.addExpense(otherId, new CreateExpenseRequest(9_000L, "Other", today));

        BudgetSpendingTools.DailySpendingTotal total = tools.dailySpendingTotal(today.toString());

        assertEquals(today, total.date());
        assertEquals(new BigDecimal("12.50"), total.totalSpentEur());
    }

    @Test
    void weeklyToolUsesMondayThroughSundayAndTruncatesTheCurrentWeekAtToday() {
        LocalDate today = LocalDate.now();
        LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate previousMonday = currentMonday.minusWeeks(1);
        LocalDate previousSunday = previousMonday.plusDays(6);
        long ownerId = accountService.requireUserId(OWNER_SUBJECT);
        budgetService.setup(ownerId, new BudgetSetupRequest(previousMonday));
        budgetService.addExpense(ownerId, new CreateExpenseRequest(500L, "Monday", previousMonday));
        budgetService.addExpense(ownerId, new CreateExpenseRequest(700L, "Sunday", previousSunday));
        budgetService.addExpense(ownerId, new CreateExpenseRequest(1_100L, "Today", today));

        BudgetSpendingTools.WeeklySpendingTotal priorWeek =
                tools.weeklySpendingTotal(previousSunday.toString());
        BudgetSpendingTools.WeeklySpendingTotal currentWeek =
                tools.weeklySpendingTotal(today.toString());

        assertEquals(previousMonday, priorWeek.weekStartDate());
        assertEquals(previousSunday, priorWeek.weekEndDate());
        assertEquals(new BigDecimal("12.00"), priorWeek.totalSpentEur());
        assertEquals(currentMonday, currentWeek.weekStartDate());
        assertEquals(today, currentWeek.weekEndDate());
        assertEquals(new BigDecimal("11.00"), currentWeek.totalSpentEur());
    }

    @Test
    void lifetimeToolUsesAuthenticatedUsersBudgetStartAndAccumulatedExpenses() {
        LocalDate today = LocalDate.now();
        LocalDate ownerStart = today.minusDays(10);
        LocalDate otherStart = today.minusDays(5);
        long ownerId = accountService.requireUserId(OWNER_SUBJECT);
        long otherId = accountService.requireUserId(OTHER_SUBJECT);
        budgetService.setup(ownerId, new BudgetSetupRequest(ownerStart));
        budgetService.setup(otherId, new BudgetSetupRequest(otherStart));
        budgetService.addExpense(ownerId, new CreateExpenseRequest(1_250L, "Budget start", ownerStart));
        budgetService.addExpense(ownerId, new CreateExpenseRequest(2_500L, "Today", today));
        budgetService.addExpense(otherId, new CreateExpenseRequest(50_000L, "Other user", today));

        BudgetSpendingTools.SpendingSinceBudgetStart total = tools.spendingSinceBudgetStart();

        assertEquals(ownerStart, total.budgetStartDate());
        assertEquals(today, total.throughDate());
        assertEquals(new BigDecimal("37.50"), total.totalSpentEur());
    }

    @Test
    void rejectsInvalidAndFutureDatesAndRequiresAuthenticatedOidcUser() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        assertThrows(IllegalArgumentException.class, () -> tools.dailySpendingTotal("not-a-date"));
        assertThrows(IllegalArgumentException.class, () -> tools.weeklySpendingTotal("2026-02-30"));
        assertThrows(IllegalArgumentException.class, () -> tools.dailySpendingTotal(tomorrow.toString()));
        assertThrows(IllegalArgumentException.class, () -> tools.weeklySpendingTotal(tomorrow.toString()));

        SecurityContextHolder.clearContext();
        assertThrows(IllegalStateException.class, () ->
                tools.dailySpendingTotal(LocalDate.now().toString()));
    }

    @Test
    void rejectsQueriesWhenAuthenticatedUserHasNoBudget() {
        String subject = "spending-tool-no-budget";
        accountService.registerOrUpdateGoogleIdentity(subject, "no-budget@example.com");
        authenticateAs(subject);

        assertThrows(IllegalStateException.class, () ->
                tools.dailySpendingTotal(LocalDate.now().toString()));
        assertThrows(IllegalStateException.class, tools::spendingSinceBudgetStart);
    }

    private void authenticateAs(String subject) {
        OidcUser user = mock(OidcUser.class);
        when(user.getSubject()).thenReturn(subject);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "test",
                        AuthorityUtils.NO_AUTHORITIES));
    }
}
