package com.example.springjpa.domain;

import com.example.springjpa.api.dto.BudgetSetupRequest;
import com.example.springjpa.api.dto.CreateExpenseRequest;
import com.example.springjpa.repository.BudgetSettingsRepository;
import com.example.springjpa.repository.BudgetUserRepository;
import com.example.springjpa.repository.ExpenseRepository;
import com.example.springjpa.service.BudgetAccountService;
import com.example.springjpa.service.BudgetService;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

@SpringBootTest
class ExpenseUserRelationshipTest {

    private static final String USER_SUBJECT = "expense-relationship-test";

    @Autowired
    private BudgetAccountService accountService;

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private BudgetSettingsRepository settingsRepository;

    @Autowired
    private BudgetUserRepository userRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void clearBudget() {
        expenseRepository.deleteAllInBatch();
        settingsRepository.deleteAllInBatch();
        accountService.registerOrUpdateGoogleIdentity(USER_SUBJECT, "expense-relationship@example.com");
    }

    @Test
    @Transactional
    void persistedExpenseNavigatesToItsUserAndUsersInverseCollection() {
        LocalDate today = LocalDate.now();
        long userId = accountService.requireUserId(USER_SUBJECT);
        budgetService.setup(userId, new BudgetSetupRequest(today));

        long expenseId = budgetService.addExpense(
                userId, new CreateExpenseRequest(1_250L, "Lunch", today)).id();
        entityManager.flush();
        entityManager.clear();

        Expense expense = expenseRepository.findById(expenseId).orElseThrow();
        assertFalse(Hibernate.isInitialized(expense.getUser()));
        assertEquals(userId, expense.getUser().getId());

        BudgetUser user = userRepository.findById(userId).orElseThrow();
        assertFalse(Hibernate.isInitialized(user.getExpenses()));
        assertEquals(1, user.getExpenses().size());
        assertEquals(expenseId, user.getExpenses().getFirst().getId());
        assertSame(user, expense.getUser());
    }
}
