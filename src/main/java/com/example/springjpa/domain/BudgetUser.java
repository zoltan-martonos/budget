package com.example.springjpa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "app_users")
public class BudgetUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_subject", unique = true)
    private String googleSubject;

    @Column(length = 320)
    private String email;

    @OneToMany(mappedBy = "user")
    private List<Expense> expenses = new ArrayList<>();

    protected BudgetUser() {
    }

    public BudgetUser(String googleSubject, String email) {
        this.googleSubject = googleSubject;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public String getEmail() {
        return email;
    }

    public List<Expense> getExpenses() {
        return expenses;
    }

    public void addExpense(Expense expense) {
        if (!Objects.equals(expense.getUser().getId(), id)) {
            throw new IllegalArgumentException("An expense can only be added to its associated user.");
        }
        expenses.add(expense);
    }

    public void claimGoogleIdentity(String googleSubject, String email) {
        if (this.googleSubject != null) {
            throw new IllegalStateException("This budget already has a Google identity.");
        }
        this.googleSubject = googleSubject;
        this.email = email;
    }

    public void updateEmail(String email) {
        this.email = email;
    }
}
