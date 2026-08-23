package com.securetrade.accessapi.repository;

import com.securetrade.accessapi.entity.TradingAgentEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TradingAgentRepository extends JpaRepository<TradingAgentEntity, UUID> {

    Optional<TradingAgentEntity> findByUserId(UUID userId);

    Optional<TradingAgentEntity> findByAgentCode(String agentCode);

    @EntityGraph(attributePaths = "user")
    Optional<TradingAgentEntity> findByUserUsername(String username);

    boolean existsByAgentCode(String agentCode);
}
