package com.example.springjpa.repository;

import com.example.springjpa.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserIdAndSpentOnBetweenOrderBySpentOnDescIdDesc(
            Long userId, LocalDate startDate, LocalDate endDate);

    List<Expense> findByUserIdAndSpentOnBetweenOrderBySpentOnAscIdAsc(
            Long userId, LocalDate startDate, LocalDate endDate);

    @Query("""
            select coalesce(sum(expense.amountCents), 0)
            from Expense expense
            where expense.userId = :userId
              and expense.spentOn between :startDate and :endDate
            """)
    long sumAmountCentsByUserIdAndSpentOnBetween(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    boolean existsByIdAndUserId(Long id, Long userId);

    void deleteByIdAndUserId(Long id, Long userId);
}
