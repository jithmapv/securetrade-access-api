package com.securetrade.accessapi.repository;

import com.securetrade.accessapi.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    Page<AuditLogEntity> findAllByOrderByTimestampDesc(Pageable pageable);

    Page<AuditLogEntity> findByActorUsername(
            String actorUsername,
            Pageable pageable);

    Page<AuditLogEntity> findByRequestId(UUID requestId, Pageable pageable);
}
