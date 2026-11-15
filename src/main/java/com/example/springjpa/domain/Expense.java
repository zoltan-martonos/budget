package com.example.springjpa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(length = 240)
    private String description;

    protected Expense() {
    }

    public Expense(Long userId, LocalDate spentOn, long amountCents, String description) {
        this.userId = userId;
        this.spentOn = spentOn;
        this.amountCents = amountCents;
        this.description = description;
    }

    public Long getUserId() {
        return userId;
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
