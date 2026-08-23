package com.securetrade.accessapi.repository;

import com.securetrade.accessapi.entity.TradingAgentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TradingAgentRepository extends JpaRepository<TradingAgentEntity, UUID> {

    Optional<TradingAgentEntity> findByUserId(UUID userId);

    Optional<TradingAgentEntity> findByAgentCode(String agentCode);

    @EntityGraph(attributePaths = "user")
    Optional<TradingAgentEntity> findByUserUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select agent from TradingAgentEntity agent where agent.id = :agentId")
    Optional<TradingAgentEntity> findByIdForUpdate(@Param("agentId") UUID agentId);

    boolean existsByAgentCode(String agentCode);
}
