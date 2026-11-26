package com.example.springjpa.repository;

import com.example.springjpa.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUser_IdAndSpentOnBetweenOrderBySpentOnDescIdDesc(
            Long userId, LocalDate startDate, LocalDate endDate);

    List<Expense> findByUser_IdAndSpentOnBetweenOrderBySpentOnAscIdAsc(
            Long userId, LocalDate startDate, LocalDate endDate);

    @Query("""
            select coalesce(sum(expense.amountCents), 0)
            from Expense expense
            where expense.user.id = :userId
              and expense.spentOn between :startDate and :endDate
            """)
    long sumAmountCentsByUser_IdAndSpentOnBetween(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            select expense.spentOn as spentOn, sum(expense.amountCents) as totalSpentCents
            from Expense expense
            where expense.user.id = :userId
              and expense.spentOn between :startDate and :endDate
            group by expense.spentOn
            order by expense.spentOn
            """)
    List<DailySpendingTotal> findDailySpendingTotals(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    boolean existsByIdAndUser_Id(Long id, Long userId);

    void deleteByIdAndUser_Id(Long id, Long userId);

    interface DailySpendingTotal {
        LocalDate getSpentOn();

        Long getTotalSpentCents();
    }
}
