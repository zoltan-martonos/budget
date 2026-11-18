package com.example.springjpa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mcp_access_tokens")
public class BudgetMcpToken {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    protected BudgetMcpToken() {
    }

    public BudgetMcpToken(Long userId, String tokenHash) {
        this.userId = userId;
        this.tokenHash = tokenHash;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void rotate(String tokenHash) {
        this.tokenHash = tokenHash;
    }
}
