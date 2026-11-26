package com.example.springjpa.service;

import com.example.springjpa.api.dto.BudgetDashboardResponse;
import com.example.springjpa.api.dto.BudgetSetupRequest;
import com.example.springjpa.api.dto.CreateExpenseRequest;
import com.example.springjpa.api.dto.DailyBudgetResponse;
import com.example.springjpa.api.dto.ExpenseResponse;
import com.example.springjpa.api.dto.WeeklySpendResponse;
import com.example.springjpa.domain.BudgetSettings;
import com.example.springjpa.domain.BudgetUser;
import com.example.springjpa.domain.Expense;
import com.example.springjpa.repository.BudgetSettingsRepository;
import com.example.springjpa.repository.BudgetUserRepository;
import com.example.springjpa.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BudgetService {

    public static final long DAILY_ALLOWANCE_CENTS = 3_000;

    private final BudgetSettingsRepository settingsRepository;
    private final BudgetUserRepository userRepository;
    private final ExpenseRepository expenseRepository;

    public BudgetService(
            BudgetSettingsRepository settingsRepository,
            BudgetUserRepository userRepository,
            ExpenseRepository expenseRepository) {
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
    }

    @Transactional(readOnly = true)
    public BudgetDashboardResponse getDashboard(Long userId, String requestedMonth) {
        LocalDate today = LocalDate.now();
        YearMonth month = parseMonth(requestedMonth, today);
        BudgetSettings settings = settingsRepository.findById(userId).orElse(null);
        if (settings == null) {
            return new BudgetDashboardResponse(true, null, today, month.toString(),
                    DAILY_ALLOWANCE_CENTS, 0, 0, 0, List.of(), List.of(), List.of());
        }

        LocalDate startDate = settings.getAllowanceStartDate();
        if (startDate.isAfter(today)) {
            return new BudgetDashboardResponse(false, startDate, today, month.toString(),
                    DAILY_ALLOWANCE_CENTS, 0, 0, 0, List.of(), List.of(), List.of());
        }

        List<Expense> allExpenses = expenseRepository
                .findByUser_IdAndSpentOnBetweenOrderBySpentOnAscIdAsc(userId, startDate, today);
        long allowanceToDate = Math.multiplyExact(
                DAILY_ALLOWANCE_CENTS, today.toEpochDay() - startDate.toEpochDay() + 1);
        long totalSpent = 0;
        long todaySpent = 0;
        Map<LocalDate, Long> spendingByDate = new HashMap<>();
        for (Expense expense : allExpenses) {
            totalSpent = Math.addExact(totalSpent, expense.getAmountCents());
            spendingByDate.merge(expense.getSpentOn(), expense.getAmountCents(), Math::addExact);
            if (expense.getSpentOn().equals(today)) {
                todaySpent = Math.addExact(todaySpent, expense.getAmountCents());
            }
        }

        List<ExpenseResponse> monthExpenses = expenseRepository
                .findByUser_IdAndSpentOnBetweenOrderBySpentOnDescIdDesc(
                        userId,
                        month.atDay(1).isBefore(startDate) ? startDate : month.atDay(1),
                        month.atEndOfMonth().isAfter(today) ? today : month.atEndOfMonth())
                .stream()
                .map(this::toResponse)
                .toList();
        List<WeeklySpendResponse> weeklySpending = getWeeklySpending(
                month, startDate, today, allExpenses);

        LocalDate summaryStart = month.atDay(1).isBefore(startDate) ? startDate : month.atDay(1);
        LocalDate summaryEnd = month.atEndOfMonth().isAfter(today) ? today : month.atEndOfMonth();
        List<DailyBudgetResponse> summaries = new ArrayList<>();
        if (!summaryStart.isAfter(summaryEnd)) {
            long balance = Math.multiplyExact(
                    DAILY_ALLOWANCE_CENTS, summaryStart.toEpochDay() - startDate.toEpochDay() + 1);
            for (LocalDate date = summaryStart; !date.isAfter(summaryEnd); date = date.plusDays(1)) {
                long spent = spendingByDate.getOrDefault(date, 0L);
                balance = Math.subtractExact(balance, spent);
                summaries.add(new DailyBudgetResponse(date, spent, balance));
                if (date.isBefore(summaryEnd)) {
                    balance = Math.addExact(balance, DAILY_ALLOWANCE_CENTS);
                }
            }
        }

        return new BudgetDashboardResponse(false, startDate, today, month.toString(),
                DAILY_ALLOWANCE_CENTS, Math.subtractExact(allowanceToDate, totalSpent),
                totalSpent, todaySpent, summaries, monthExpenses, weeklySpending);
    }

    private List<WeeklySpendResponse> getWeeklySpending(
            YearMonth month,
            LocalDate startDate,
            LocalDate today,
            List<Expense> allExpenses) {
        LocalDate firstAvailableDay = month.atDay(1).isBefore(startDate) ? startDate : month.atDay(1);
        LocalDate lastAvailableDay = month.atEndOfMonth().isAfter(today) ? today : month.atEndOfMonth();
        if (firstAvailableDay.isAfter(lastAvailableDay)) {
            return List.of();
        }

        Map<LocalDate, Long> spendingByWeek = new HashMap<>();
        for (Expense expense : allExpenses) {
            LocalDate weekStart = expense.getSpentOn().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            spendingByWeek.merge(weekStart, expense.getAmountCents(), Math::addExact);
        }

        LocalDate weekStart = firstAvailableDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastWeekStart = lastAvailableDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<WeeklySpendResponse> weeklySpending = new ArrayList<>();
        while (!weekStart.isAfter(lastWeekStart)) {
            LocalDate weekEnd = weekStart.plusDays(6);
            LocalDate allowanceStart = weekStart.isBefore(startDate) ? startDate : weekStart;
            LocalDate allowanceEnd = weekEnd.isAfter(today) ? today : weekEnd;
            long allowanceDays = allowanceEnd.toEpochDay() - allowanceStart.toEpochDay() + 1;
            weeklySpending.add(new WeeklySpendResponse(
                    weekStart,
                    weekEnd,
                    spendingByWeek.getOrDefault(weekStart, 0L),
                    Math.multiplyExact(DAILY_ALLOWANCE_CENTS, allowanceDays)));
            weekStart = weekStart.plusWeeks(1);
        }
        return weeklySpending;
    }

    @Transactional
    public void setup(Long userId, BudgetSetupRequest request) {
        if (settingsRepository.existsById(userId)) {
            throw new IllegalStateException("The budget has already been set up.");
        }
        if (request.startDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("The allowance start date cannot be in the future.");
        }
        settingsRepository.save(new BudgetSettings(userId, request.startDate()));
    }

    @Transactional
    public void changeStartDate(Long userId, BudgetSetupRequest request) {
        LocalDate newStartDate = request.startDate();
        if (newStartDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("The allowance start date cannot be in the future.");
        }
        BudgetSettings settings = settingsRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Set up your budget before changing its start date."));
        if (!newStartDate.isBefore(settings.getAllowanceStartDate())) {
            throw new IllegalArgumentException("The new allowance start date must be earlier than the current start date.");
        }
        settings.changeAllowanceStartDate(newStartDate);
    }

    @Transactional
    public ExpenseResponse addExpense(Long userId, CreateExpenseRequest request) {
        BudgetSettings settings = settingsRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Set up your budget before adding expenses."));
        LocalDate today = LocalDate.now();
        if (request.spentOn().isAfter(today)) {
            throw new IllegalArgumentException("The expense date cannot be in the future.");
        }
        if (request.spentOn().isBefore(settings.getAllowanceStartDate())) {
            throw new IllegalArgumentException("The expense date cannot be before your allowance start date.");
        }
        String description = request.description() == null || request.description().isBlank()
                ? null : request.description().trim();
        BudgetUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("The authenticated budget user no longer exists."));
        Expense expense = new Expense(user, request.spentOn(), request.amountCents(), description);
        user.addExpense(expense);
        return toResponse(expenseRepository.save(
                expense));
    }

    @Transactional
    public void deleteExpense(Long userId, Long expenseId) {
        if (!expenseRepository.existsByIdAndUser_Id(expenseId, userId)) {
            throw new ExpenseNotFoundException(expenseId);
        }
        expenseRepository.deleteByIdAndUser_Id(expenseId, userId);
    }

    private YearMonth parseMonth(String requestedMonth, LocalDate today) {
        if (requestedMonth == null || requestedMonth.isBlank()) {
            return YearMonth.from(today);
        }
        try {
            return YearMonth.parse(requestedMonth);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Month must use the yyyy-MM format.");
        }
    }

    private ExpenseResponse toResponse(Expense expense) {
        return new ExpenseResponse(expense.getId(), expense.getSpentOn(),
                expense.getAmountCents(), expense.getDescription());
    }
}
