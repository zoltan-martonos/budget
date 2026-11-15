package com.example.springjpa.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

@Component
public class BudgetSpendingTools {

    private final SpendingQueryService spendingQueryService;

    public BudgetSpendingTools(SpendingQueryService spendingQueryService) {
        this.spendingQueryService = spendingQueryService;
    }

    @Tool(
            name = "daily_spending_total",
            description = "Get the authenticated user's total spending for one calendar date. "
                    + "Provide the date in ISO format YYYY-MM-DD. The result is a euro amount with "
                    + "exactly two decimal places.")
    public DailySpendingTotal dailySpendingTotal(
            @ToolParam(description = "Calendar date in YYYY-MM-DD format") String date) {
        LocalDate day = parseDate(date);
        long totalCents = spendingQueryService.totalSpentCents(authenticatedUser(), day, day);
        return new DailySpendingTotal(day, euroAmount(totalCents));
    }

    @Tool(
            name = "weekly_spending_total",
            description = "Get the authenticated user's total spending for the Monday-to-Sunday week "
                    + "containing a date. The current week ends today. Provide the date in ISO format "
                    + "YYYY-MM-DD. The result is a euro amount with exactly two decimal places.")
    public WeeklySpendingTotal weeklySpendingTotal(
            @ToolParam(description = "A date in the week to total, in YYYY-MM-DD format") String date) {
        LocalDate requestedDate = parseDate(date);
        LocalDate today = LocalDate.now();
        if (requestedDate.isAfter(today)) {
            throw new IllegalArgumentException("The query date cannot be in the future.");
        }
        LocalDate weekStart = requestedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate effectiveEnd = weekEnd.isAfter(today) ? today : weekEnd;

        long totalCents = spendingQueryService.totalSpentCents(
                authenticatedUser(), weekStart, effectiveEnd);
        return new WeeklySpendingTotal(weekStart, effectiveEnd, euroAmount(totalCents));
    }

    @Tool(
            name = "recent_spending_total",
            description = "Get the authenticated user's total spending over the last N weeks, as a rolling "
                    + "period of N*7 calendar days ending today (both endpoints inclusive). Use this for "
                    + "recent-period questions such as 'in the last 3 weeks'; do not use the "
                    + "spending_since_budget_start tool for these. The period is clipped to the user's "
                    + "budget start date when newer, and the returned startDate is the effective start. "
                    + "Weeks must be between 1 and 52. The amount is in euros with exactly two decimals.")
    public RecentSpendingTotal recentSpendingTotal(
            @ToolParam(description = "Number of weeks, from 1 to 52") int weeks) {
        SpendingQueryService.SpendingPeriodTotal total =
                spendingQueryService.totalSpentForRecentWeeks(authenticatedUser(), weeks);
        return new RecentSpendingTotal(
                weeks, total.startDate(), total.endDate(), euroAmount(total.totalSpentCents()));
    }

    @Tool(
            name = "spending_since_budget_start",
            description = "Get the authenticated user's total spending from their budget allowance start date "
                    + "through today, inclusive. Use this for lifetime, all-time, or since-inception spending "
                    + "questions. Describe the result as spending since the budget started on the returned date; "
                    + "do not use this for bounded recent periods such as 'last 3 weeks'. This is not the "
                    + "Google account creation date. "
                    + "The result is a euro amount with exactly two decimal places.")
    public SpendingSinceBudgetStart spendingSinceBudgetStart() {
        SpendingQueryService.SpendingPeriodTotal total =
                spendingQueryService.totalSpentSinceBudgetStart(authenticatedUser());
        return new SpendingSinceBudgetStart(
                total.startDate(), total.endDate(), euroAmount(total.totalSpentCents()));
    }

    @Tool(
            name = "highest_spending_date",
            description = "Find every date tied for the highest total spending in the authenticated user's "
                    + "recorded budget history, from budget start through today inclusive. Use this for "
                    + "questions asking which day/date had the most or highest spending. Returns the actual "
                    + "date, weekday name, and total in euros. If there are no recorded expenses, the dates "
                    + "list is empty; never substitute today or a zero-spending day.")
    public HighestSpendingDates highestSpendingDate() {
        List<HighestSpendingDay> days = spendingQueryService.highestSpendingDates(authenticatedUser())
                .stream()
                .map(total -> new HighestSpendingDay(
                        total.date(),
                        total.date().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        euroAmount(total.totalSpentCents())))
                .toList();
        return new HighestSpendingDates(days);
    }

    private OidcUser authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof OidcUser user)) {
            throw new IllegalStateException("An authenticated OIDC user is required.");
        }
        return user;
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException("Date must be a valid calendar date in YYYY-MM-DD format.");
        }
    }

    private BigDecimal euroAmount(long amountCents) {
        return BigDecimal.valueOf(amountCents, 2);
    }

    public record DailySpendingTotal(LocalDate date, BigDecimal totalSpentEur) {
    }

    public record WeeklySpendingTotal(
            LocalDate weekStartDate,
            LocalDate weekEndDate,
            BigDecimal totalSpentEur) {
    }

    public record SpendingSinceBudgetStart(
            LocalDate budgetStartDate,
            LocalDate throughDate,
            BigDecimal totalSpentEur) {
    }

    public record RecentSpendingTotal(
            int weeks,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal totalSpentEur) {
    }

    public record HighestSpendingDates(List<HighestSpendingDay> dates) {
    }

    public record HighestSpendingDay(
            LocalDate date,
            String dayOfWeek,
            BigDecimal totalSpentEur) {
    }
}
