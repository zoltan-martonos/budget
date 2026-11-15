package com.example.springjpa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "assistant_exchanges")
public class AssistantExchange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Lob
    @Column(nullable = false)
    private String question;

    @Lob
    @Column(nullable = false)
    private String response;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AssistantExchange() {
    }

    public AssistantExchange(Long userId, String question, String response) {
        this.userId = userId;
        this.question = question;
        this.response = response;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getQuestion() {
        return question;
    }

    public String getResponse() {
        return response;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
