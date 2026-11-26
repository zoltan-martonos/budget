package com.example.springjpa.api;

import com.example.springjpa.repository.BudgetSettingsRepository;
import com.example.springjpa.repository.ExpenseRepository;
import com.example.springjpa.service.BudgetAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BudgetControllerTest {

    private static final String OWNER_SUBJECT = "controller-test-owner";
    private static final String OWNER_EMAIL = "controller-owner@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BudgetSettingsRepository settingsRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private BudgetAccountService accountService;

    @BeforeEach
    void clearBudget() {
        expenseRepository.deleteAllInBatch();
        settingsRepository.deleteAllInBatch();
        accountService.registerOrUpdateGoogleIdentity(OWNER_SUBJECT, OWNER_EMAIL);
    }

    @Test
    void requiresAnExplicitStartDateAndCalculatesCarryoverAndOverspending() throws Exception {
        LocalDate today = LocalDate.now();

        mockMvc.perform(asOwner(get("/api/budget")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(true));

        mockMvc.perform(writeAsOwner(post("/api/budget/setup"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"%s"}
                                """.formatted(today.minusDays(2))))
                .andExpect(status().isCreated());

        mockMvc.perform(writeAsOwner(post("/api/budget/expenses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents":1000,"description":"Lunch","spentOn":"%s"}
                                """.formatted(today.minusDays(2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user").doesNotExist());

        mockMvc.perform(writeAsOwner(post("/api/budget/expenses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents":8500,"description":"A bigger purchase","spentOn":"%s"}
                                """.formatted(today)))
                .andExpect(status().isCreated());

        mockMvc.perform(asOwner(get("/api/budget")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(false))
                .andExpect(jsonPath("$.dailyAllowanceCents").value(3000))
                .andExpect(jsonPath("$.totalSpentCents").value(9500))
                .andExpect(jsonPath("$.todaySpentCents").value(8500))
                .andExpect(jsonPath("$.currentBalanceCents").value(-500))
                .andExpect(jsonPath("$.dailySummaries.length()").value(3))
                .andExpect(jsonPath("$.dailySummaries[0].balanceCents").value(2000))
                .andExpect(jsonPath("$.dailySummaries[1].balanceCents").value(5000))
                .andExpect(jsonPath("$.dailySummaries[2].balanceCents").value(-500));

        long expenseId = expenseRepository
                .findByUser_IdAndSpentOnBetweenOrderBySpentOnDescIdDesc(
                        accountService.requireUserId(OWNER_SUBJECT), today, today).get(0).getId();
        mockMvc.perform(writeAsOwner(delete("/api/budget/expenses/{id}", expenseId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(asOwner(get("/api/budget")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpentCents").value(1000))
                .andExpect(jsonPath("$.currentBalanceCents").value(8000));
    }

    @Test
    void rejectsNegativeAmountsAndExpensesOutsideTheBudgetPeriod() throws Exception {
        LocalDate today = LocalDate.now();
        mockMvc.perform(writeAsOwner(post("/api/budget/setup"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"%s"}
                                """.formatted(today)))
                .andExpect(status().isCreated());

        mockMvc.perform(writeAsOwner(post("/api/budget/expenses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents":-1,"spentOn":"%s"}
                                """.formatted(today)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(writeAsOwner(post("/api/budget/expenses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents":1,"spentOn":"%s"}
                                """.formatted(today.plusDays(1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingStartDateEarlierRecalculatesTheBalance() throws Exception {
        LocalDate today = LocalDate.now();
        mockMvc.perform(writeAsOwner(post("/api/budget/setup"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"%s"}
                                """.formatted(today)))
                .andExpect(status().isCreated());

        mockMvc.perform(writeAsOwner(post("/api/budget/expenses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents":1000,"spentOn":"%s"}
                                """.formatted(today)))
                .andExpect(status().isCreated());

        mockMvc.perform(writeAsOwner(put("/api/budget/start-date"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"%s"}
                                """.formatted(today.minusDays(2))))
                .andExpect(status().isNoContent());

        mockMvc.perform(asOwner(get("/api/budget")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value(today.minusDays(2).toString()))
                .andExpect(jsonPath("$.currentBalanceCents").value(8000));
    }

    @Test
    void reportsWeeklySpendingForSelectedMonth() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1).minusMonths(1);
        mockMvc.perform(writeAsOwner(post("/api/budget/setup"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"%s"}
                                """.formatted(monthStart)))
                .andExpect(status().isCreated());

        mockMvc.perform(writeAsOwner(post("/api/budget/expenses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents":1200,"spentOn":"%s"}
                                """.formatted(monthStart)))
                .andExpect(status().isCreated());
        mockMvc.perform(writeAsOwner(post("/api/budget/expenses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents":800,"spentOn":"%s"}
                                """.formatted(monthStart.plusDays(1))))
                .andExpect(status().isCreated());
        mockMvc.perform(writeAsOwner(post("/api/budget/expenses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents":3400,"spentOn":"%s"}
                                """.formatted(monthStart.plusDays(7))))
                .andExpect(status().isCreated());

        mockMvc.perform(asOwner(get("/api/budget?month=%s".formatted(monthStart.toString().substring(0, 7)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses.length()").value(3))
                .andExpect(jsonPath("$.weeklySpending.length()").isNotEmpty())
                .andExpect(jsonPath("$.weeklySpending[0].spentCents").value(2000))
                .andExpect(jsonPath("$.weeklySpending[1].spentCents").value(3400))
                .andExpect(jsonPath("$.weeklySpending[0].allowanceCents").value(
                        (monthStart.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
                                .toEpochDay() - monthStart.toEpochDay() + 1) * 3000))
                .andExpect(jsonPath("$.weeklySpending[1].allowanceCents").value(21000))
                .andExpect(jsonPath("$.weeklySpending[0].weekStart").value(
                        monthStart.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString()))
                .andExpect(jsonPath("$.weeklySpending[1].weekStart").value(
                        monthStart.plusDays(7)
                                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString()))
                .andExpect(jsonPath("$.weeklySpending[0].weekEnd").value(
                        monthStart.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)).toString()));
    }

    @Test
    void includesExpensesAcrossMonthBoundaryInWeeklySpending() throws Exception {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        while (monthStart.getDayOfWeek() == java.time.DayOfWeek.MONDAY) {
            monthStart = monthStart.minusMonths(1);
        }
        LocalDate budgetStart = monthStart.minusDays(14);
        mockMvc.perform(writeAsOwner(post("/api/budget/setup"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"%s"}
                                """.formatted(budgetStart)))
                .andExpect(status().isCreated());

        mockMvc.perform(writeAsOwner(post("/api/budget/expenses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents":3400,"spentOn":"%s"}
                                """.formatted(monthStart.minusDays(1))))
                .andExpect(status().isCreated());
        mockMvc.perform(writeAsOwner(post("/api/budget/expenses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents":2100,"spentOn":"%s"}
                                """.formatted(monthStart)))
                .andExpect(status().isCreated());

        String month = monthStart.toString().substring(0, 7);
        mockMvc.perform(asOwner(get("/api/budget?month=%s".formatted(month))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses.length()").value(1))
                .andExpect(jsonPath("$.weeklySpending[0].weekStart").value(
                        monthStart.minusDays(1)
                                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString()))
                .andExpect(jsonPath("$.weeklySpending[0].spentCents").value(5500))
                .andExpect(jsonPath("$.weeklySpending[0].allowanceCents").value(21000));
    }

    @Test
    void cannotMoveStartDateLater() throws Exception {
        LocalDate today = LocalDate.now();
        mockMvc.perform(writeAsOwner(post("/api/budget/setup"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"%s"}
                                """.formatted(today.minusDays(1))))
                .andExpect(status().isCreated());

        mockMvc.perform(writeAsOwner(put("/api/budget/start-date"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"%s"}
                                """.formatted(today)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void firstTimeAccountGetsAnIndependentBudgetAndCannotReadOrDeleteAnotherUsersExpenses() throws Exception {
        LocalDate today = LocalDate.now();
        long ownerId = accountService.requireUserId(OWNER_SUBJECT);
        accountService.registerOrUpdateGoogleIdentity("new-account-subject", "new-account@example.com");
        long newUserId = accountService.requireUserId("new-account-subject");

        mockMvc.perform(writeAsOwner(post("/api/budget/setup"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"%s"}
                                """.formatted(today)))
                .andExpect(status().isCreated());
        String ownerExpense = mockMvc.perform(writeAsOwner(post("/api/budget/expenses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents":1200,"description":"Owner expense","spentOn":"%s"}
                                """.formatted(today)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long ownerExpenseId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(ownerExpense).get("id").asLong();

        mockMvc.perform(asUser(get("/api/budget"), "new-account-subject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(true))
                .andExpect(jsonPath("$.expenses.length()").value(0));
        mockMvc.perform(writeAsUser(post("/api/budget/setup"), "new-account-subject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"%s"}
                                """.formatted(today)))
                .andExpect(status().isCreated());
        mockMvc.perform(writeAsUser(post("/api/budget/expenses"), "new-account-subject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents":2400,"description":"Private expense","spentOn":"%s","userId":%d}
                                """.formatted(today, ownerId)))
                .andExpect(status().isCreated());
        mockMvc.perform(writeAsUser(put("/api/budget/start-date"), "new-account-subject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"%s"}
                                """.formatted(today.minusDays(1))))
                .andExpect(status().isNoContent());

        mockMvc.perform(asUser(get("/api/budget"), "new-account-subject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.totalSpentCents").value(2400))
                .andExpect(jsonPath("$.expenses.length()").value(1));
        mockMvc.perform(writeAsUser(delete("/api/budget/expenses/{id}", ownerExpenseId), "new-account-subject"))
                .andExpect(status().isNotFound());
        mockMvc.perform(asOwner(get("/api/budget")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value(today.toString()))
                .andExpect(jsonPath("$.totalSpentCents").value(1200))
                .andExpect(jsonPath("$.expenses.length()").value(1));
        org.junit.jupiter.api.Assertions.assertTrue(ownerId != newUserId);
    }

    private MockHttpServletRequestBuilder asOwner(MockHttpServletRequestBuilder request) {
        return asUser(request, OWNER_SUBJECT);
    }

    private MockHttpServletRequestBuilder writeAsOwner(MockHttpServletRequestBuilder request) {
        return asOwner(request).with(csrf());
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request, String subject) {
        return request.with(oidcLogin().idToken(idToken -> idToken.subject(subject)));
    }

    private MockHttpServletRequestBuilder writeAsUser(
            MockHttpServletRequestBuilder request, String subject) {
        return asUser(request, subject).with(csrf());
    }
}
