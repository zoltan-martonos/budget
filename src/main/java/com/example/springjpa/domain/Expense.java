package com.example.springjpa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private BudgetUser user;

    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(length = 240)
    private String description;

    protected Expense() {
    }

    public Expense(BudgetUser user, LocalDate spentOn, long amountCents, String description) {
        this.user = Objects.requireNonNull(user, "user");
        this.spentOn = spentOn;
        this.amountCents = amountCents;
        this.description = description;
    }

    public Long getUserId() {
        return user.getId();
    }

    public BudgetUser getUser() {
        return user;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getSpentOn() {
        return spentOn;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getDescription() {
        return description;
    }
}
