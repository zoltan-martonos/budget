package com.example.springjpa.api;

import com.example.springjpa.api.dto.BudgetDashboardResponse;
import com.example.springjpa.api.dto.BudgetSetupRequest;
import com.example.springjpa.api.dto.CreateExpenseRequest;
import com.example.springjpa.api.dto.ExpenseResponse;
import com.example.springjpa.service.BudgetAccountService;
import com.example.springjpa.service.BudgetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budget")
public class BudgetController {

    private final BudgetService budgetService;
    private final BudgetAccountService accountService;

    public BudgetController(BudgetService budgetService, BudgetAccountService accountService) {
        this.budgetService = budgetService;
        this.accountService = accountService;
    }

    @GetMapping
    public BudgetDashboardResponse getDashboard(
            @AuthenticationPrincipal OidcUser user,
            @RequestParam(required = false) String month) {
        return budgetService.getDashboard(userId(user), month);
    }

    @PostMapping("/setup")
    public ResponseEntity<Void> setup(
            @AuthenticationPrincipal OidcUser user,
            @Valid @RequestBody BudgetSetupRequest request) {
        budgetService.setup(userId(user), request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/start-date")
    public ResponseEntity<Void> changeStartDate(
            @AuthenticationPrincipal OidcUser user,
            @Valid @RequestBody BudgetSetupRequest request) {
        budgetService.changeStartDate(userId(user), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/expenses")
    public ResponseEntity<ExpenseResponse> addExpense(
            @AuthenticationPrincipal OidcUser user,
            @Valid @RequestBody CreateExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(budgetService.addExpense(userId(user), request));
    }

    @DeleteMapping("/expenses/{expenseId}")
    public ResponseEntity<Void> deleteExpense(
            @AuthenticationPrincipal OidcUser user,
            @PathVariable Long expenseId) {
        budgetService.deleteExpense(userId(user), expenseId);
        return ResponseEntity.noContent().build();
    }

    private long userId(OidcUser user) {
        return accountService.requireUserId(user.getSubject());
    }
}
