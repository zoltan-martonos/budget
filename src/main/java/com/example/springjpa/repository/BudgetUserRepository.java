package com.example.springjpa.repository;

import com.example.springjpa.domain.BudgetUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BudgetUserRepository extends JpaRepository<BudgetUser, Long> {

    Optional<BudgetUser> findByGoogleSubject(String googleSubject);
}
