package com.securetrade.accessapi.service;

import com.securetrade.accessapi.dto.response.AuditLogResponse;
import com.securetrade.accessapi.entity.AuditLogEntity;
import com.securetrade.accessapi.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    private static final UUID AUDIT_ID =
            UUID.fromString("234e66bd-f6b9-451e-a3fd-b6260517ba80");
    private static final UUID REQUEST_ID =
            UUID.fromString("544df5ed-9068-4345-a721-42dac36fa45c");
    private static final Instant TIMESTAMP =
            Instant.parse("2026-08-23T15:00:00Z");

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    void logActionSavesAllFields() {
        auditLogService.logAction(
                REQUEST_ID,
                "agent.one",
                AuditLogService.TRADE_EVALUATION,
                null,
                "APPROVED",
                "EXEC_PASS_STANDARD");

        ArgumentCaptor<AuditLogEntity> auditCaptor =
                ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLogEntity savedAudit = auditCaptor.getValue();

        assertThat(savedAudit.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(savedAudit.getActorUsername()).isEqualTo("agent.one");
        assertThat(savedAudit.getAction())
                .isEqualTo(AuditLogService.TRADE_EVALUATION);
        assertThat(savedAudit.getPreviousState()).isNull();
        assertThat(savedAudit.getNewState()).isEqualTo("APPROVED");
        assertThat(savedAudit.getDetails()).isEqualTo("EXEC_PASS_STANDARD");
    }

    @Test
    void logActionAllowsOptionalFields() {
        auditLogService.logAction(
                null,
                "admin.one",
                AuditLogService.AGENT_STATUS_CHANGE,
                null,
                "ACTIVE",
                null);

        ArgumentCaptor<AuditLogEntity> auditCaptor =
                ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLogEntity savedAudit = auditCaptor.getValue();

        assertThat(savedAudit.getRequestId()).isNull();
        assertThat(savedAudit.getPreviousState()).isNull();
        assertThat(savedAudit.getDetails()).isNull();
    }

    @Test
    void getAuditLogsKeepsPageData() {
        PageRequest pageable = PageRequest.of(1, 2);
        AuditLogEntity audit = createAudit("agent.one");
        Page<AuditLogEntity> entityPage =
                new PageImpl<>(List.of(audit), pageable, 3);
        when(auditLogRepository.findAllByOrderByTimestampDesc(pageable))
                .thenReturn(entityPage);

        Page<AuditLogResponse> responsePage =
                auditLogService.getAuditLogs(pageable);

        assertThat(responsePage.getNumber()).isEqualTo(1);
        assertThat(responsePage.getSize()).isEqualTo(2);
        assertThat(responsePage.getTotalElements()).isEqualTo(3);
        assertThat(responsePage.getContent()).hasSize(1);
        assertResponse(responsePage.getContent().get(0), "agent.one");
    }

    @Test
    void getAuditLogsByActorUsesActorFilter() {
        PageRequest pageable = PageRequest.of(0, 10);
        AuditLogEntity audit = createAudit("admin.one");
        when(auditLogRepository.findByActorUsername("admin.one", pageable))
                .thenReturn(new PageImpl<>(List.of(audit), pageable, 1));

        Page<AuditLogResponse> responsePage =
                auditLogService.getAuditLogsByActor("admin.one", pageable);

        assertThat(responsePage.getTotalElements()).isOne();
        assertResponse(responsePage.getContent().get(0), "admin.one");
        verify(auditLogRepository).findByActorUsername("admin.one", pageable);
    }

    private AuditLogEntity createAudit(String actorUsername) {
        AuditLogEntity audit = new AuditLogEntity(
                REQUEST_ID,
                actorUsername,
                AuditLogService.ADMIN_OVERRIDE,
                "MANUAL_REVIEW",
                "APPROVED",
                "Reviewed by admin");
        audit.setId(AUDIT_ID);
        audit.setTimestamp(TIMESTAMP);
        return audit;
    }

    private void assertResponse(
            AuditLogResponse response,
            String actorUsername) {

        assertThat(response.getId()).isEqualTo(AUDIT_ID);
        assertThat(response.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(response.getActorUsername()).isEqualTo(actorUsername);
        assertThat(response.getAction()).isEqualTo(AuditLogService.ADMIN_OVERRIDE);
        assertThat(response.getPreviousState()).isEqualTo("MANUAL_REVIEW");
        assertThat(response.getNewState()).isEqualTo("APPROVED");
        assertThat(response.getDetails()).isEqualTo("Reviewed by admin");
        assertThat(response.getTimestamp()).isEqualTo(TIMESTAMP);
    }
}
