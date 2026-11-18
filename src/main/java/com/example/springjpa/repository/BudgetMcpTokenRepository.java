package com.example.springjpa.repository;

import com.example.springjpa.domain.BudgetMcpToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BudgetMcpTokenRepository extends JpaRepository<BudgetMcpToken, Long> {

    Optional<BudgetMcpToken> findByTokenHash(String tokenHash);
}
