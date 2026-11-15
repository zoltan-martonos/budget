package com.example.springjpa.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.sql.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BudgetOwnershipMigrationTest {

    @Test
    void preservesSingletonBudgetAndExpensesUnderReservedLegacyOwner() {
        String databaseUrl = "jdbc:h2:mem:budget-migration-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(databaseUrl, "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway.configure()
                .dataSource(dataSource)
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();

        LocalDate startDate = LocalDate.of(2024, 2, 3);
        LocalDate expenseDate = LocalDate.of(2024, 2, 4);
        jdbc.update("INSERT INTO budget_settings (id, allowance_start_date) VALUES (1, ?)",
                Date.valueOf(startDate));
        jdbc.update("INSERT INTO expenses (spent_on, amount_cents, description) VALUES (?, ?, ?)",
                Date.valueOf(expenseDate), 1250L, "Preserved legacy expense");
        long expenseId = jdbc.queryForObject("SELECT id FROM expenses", Long.class);

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertEquals(1L, jdbc.queryForObject("SELECT id FROM app_users", Long.class));
        assertEquals(startDate, jdbc.queryForObject(
                "SELECT allowance_start_date FROM budget_settings WHERE user_id = 1", LocalDate.class));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE id = ? AND user_id = 1 AND spent_on = ? "
                        + "AND amount_cents = 1250 AND description = ?",
                Long.class, expenseId, Date.valueOf(expenseDate), "Preserved legacy expense"));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM legacy_budget_settings WHERE id = 1", Long.class));
        assertEquals(1758992273, jdbc.queryForObject(
                "SELECT \"checksum\" FROM \"flyway_schema_history\" "
                        + "WHERE \"version\" = '2' AND \"success\" = TRUE",
                Integer.class));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '3' AND \"success\" = TRUE",
                Long.class));
    }
}
