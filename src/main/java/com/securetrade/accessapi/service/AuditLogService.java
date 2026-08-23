package com.securetrade.accessapi.service;

import com.securetrade.accessapi.dto.response.AuditLogResponse;
import com.securetrade.accessapi.entity.AuditLogEntity;
import com.securetrade.accessapi.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditLogService {

    public static final String TRADE_EVALUATION = "TRADE_EVALUATION";
    public static final String ADMIN_OVERRIDE = "ADMIN_OVERRIDE";
    public static final String AGENT_STATUS_CHANGE = "AGENT_STATUS_CHANGE";
    public static final String AGENT_REGISTRATION = "AGENT_REGISTRATION";

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void logAction(
            UUID requestId,
            String actorUsername,
            String action,
            String previousState,
            String newState,
            String details) {

        // Save audit log to database
        AuditLogEntity auditLog = new AuditLogEntity(
                requestId,
                actorUsername,
                action,
                previousState,
                newState,
                details);
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogs(Pageable pageable) {
        // Get audit logs for admin
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogsByActor(
            String actorUsername,
            Pageable pageable) {

        // Get audit logs for one actor
        return auditLogRepository.findByActorUsername(actorUsername, pageable)
                .map(this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLogEntity auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getRequestId(),
                auditLog.getActorUsername(),
                auditLog.getAction(),
                auditLog.getPreviousState(),
                auditLog.getNewState(),
                auditLog.getDetails(),
                auditLog.getTimestamp());
    }
}
