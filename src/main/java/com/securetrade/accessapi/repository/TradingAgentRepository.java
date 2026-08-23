package com.securetrade.accessapi.repository;

import com.securetrade.accessapi.entity.TradingAgentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TradingAgentRepository extends JpaRepository<TradingAgentEntity, UUID> {

    Optional<TradingAgentEntity> findByUserId(UUID userId);

    Optional<TradingAgentEntity> findByAgentCode(String agentCode);

    boolean existsByAgentCode(String agentCode);
}
