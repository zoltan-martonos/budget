package com.example.springjpa.repository;

import com.example.springjpa.domain.AssistantExchange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssistantExchangeRepository extends JpaRepository<AssistantExchange, Long> {

    List<AssistantExchange> findAllByUserIdOrderByCreatedAtAsc(Long userId);
}
