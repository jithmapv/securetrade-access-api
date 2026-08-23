package com.securetrade.accessapi.repository;

import com.securetrade.accessapi.entity.AccessRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccessRequestRepository extends JpaRepository<AccessRequestEntity, UUID> {

    Page<AccessRequestEntity> findByAgentId(UUID agentId, Pageable pageable);

    Optional<AccessRequestEntity> findByAgentIdAndIdempotencyKey(
            UUID agentId,
            String idempotencyKey);
}
