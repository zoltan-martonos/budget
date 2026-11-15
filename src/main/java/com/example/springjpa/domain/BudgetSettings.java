package com.example.springjpa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "budget_settings")
public class BudgetSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "allowance_start_date", nullable = false)
    private LocalDate allowanceStartDate;

    protected BudgetSettings() {
    }

    public BudgetSettings(Long userId, LocalDate allowanceStartDate) {
        this.userId = userId;
        this.allowanceStartDate = allowanceStartDate;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getAllowanceStartDate() {
        return allowanceStartDate;
    }

    public void changeAllowanceStartDate(LocalDate allowanceStartDate) {
        this.allowanceStartDate = allowanceStartDate;
    }
}
