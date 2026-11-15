package com.example.springjpa.service;

public class ExpenseNotFoundException extends RuntimeException {

    public ExpenseNotFoundException(Long expenseId) {
        super("Expense " + expenseId + " was not found.");
    }
}
