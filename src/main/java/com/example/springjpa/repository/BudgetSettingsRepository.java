package com.example.springjpa.repository;

import com.example.springjpa.domain.BudgetSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetSettingsRepository extends JpaRepository<BudgetSettings, Long> {
}
