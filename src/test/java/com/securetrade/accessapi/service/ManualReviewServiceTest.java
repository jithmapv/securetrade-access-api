package com.securetrade.accessapi.service;

import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.DecisionResult;
import com.securetrade.accessapi.common.enums.TradeType;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.common.exception.InvalidRequestException;
import com.securetrade.accessapi.common.exception.ResourceNotFoundException;
import com.securetrade.accessapi.dto.request.AdminOverrideRequest;
import com.securetrade.accessapi.dto.response.AccessRequestResponse;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.entity.UserEntity;
import com.securetrade.accessapi.repository.AccessRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualReviewServiceTest {

    private static final UUID USER_ID =
            UUID.fromString("c66b3a02-8569-49d5-851d-e30fb3bdd212");
    private static final UUID AGENT_ID =
            UUID.fromString("eb4fba3c-4399-4654-b60c-c34b024da940");
    private static final UUID REQUEST_ID =
            UUID.fromString("561fc415-f50f-47ec-aa11-e5b79ef708c7");
    private static final Instant CREATED_AT = Instant.parse("2026-08-23T14:00:00Z");
    private static final String ADMIN_USERNAME = "admin.one";

    @Mock
    private AccessRequestRepository accessRequestRepository;

    @Mock
    private AccessRequestPersistenceService persistenceService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ManualReviewService manualReviewService;

    private AccessRequestEntity request;

    @BeforeEach
    void setUp() {
        UserEntity user = new UserEntity(
                "agent.one",
                "password-hash",
                UserRole.TRADING_AGENT,
                AgentStatus.ACTIVE);
        user.setId(USER_ID);

        TradingAgentEntity agent = new TradingAgentEntity(
                user,
                "AGT-008",
                "Agent Eight",
                "MOMENTUM",
                new BigDecimal("5000000.00"));
        agent.setId(AGENT_ID);

        request = new AccessRequestEntity(
                agent,
                "AAPL",
                TradeType.BUY,
                new BigDecimal("1500000.00"),
                new BigDecimal("0.50"),
                DecisionResult.MANUAL_REVIEW,
                "FLAG_HIGH_VOL_RISK",
                "manual-review-key");
        request.setId(REQUEST_ID);
        request.setCreatedAt(CREATED_AT);
    }

    @Test
    void manualReviewCanBeApproved() {
        assertSuccessfulOverride(
                DecisionResult.APPROVED,
                "OVERRIDE_ADMIN_APPROVED");
    }

    @Test
    void manualReviewCanBeRejected() {
        assertSuccessfulOverride(
                DecisionResult.REJECTED,
                "OVERRIDE_RISK_EXCEEDED");
    }

    @ParameterizedTest
    @EnumSource(
            value = DecisionResult.class,
            names = {"APPROVED", "REJECTED"})
    void completedRequestCannotBeOverridden(DecisionResult currentOutcome) {
        request.setOutcome(currentOutcome);
        AdminOverrideRequest overrideRequest = new AdminOverrideRequest(
                DecisionResult.APPROVED,
                "OVERRIDE_ADMIN_APPROVED",
                null);
        when(accessRequestRepository.findByIdForUpdate(REQUEST_ID))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> manualReviewService.processOverride(
                REQUEST_ID,
                overrideRequest,
                ADMIN_USERNAME))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Only requests in MANUAL_REVIEW status can be overridden");

        verify(accessRequestRepository, never())
                .save(any(AccessRequestEntity.class));
        verifyNoInteractions(persistenceService, auditLogService);
    }

    @Test
    void currentStateCheckRunsBeforeTargetCheck() {
        request.setOutcome(DecisionResult.APPROVED);
        AdminOverrideRequest overrideRequest = new AdminOverrideRequest(
                DecisionResult.MANUAL_REVIEW,
                "OVERRIDE_ADMIN_APPROVED",
                null);
        when(accessRequestRepository.findByIdForUpdate(REQUEST_ID))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> manualReviewService.processOverride(
                REQUEST_ID,
                overrideRequest,
                ADMIN_USERNAME))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Only requests in MANUAL_REVIEW status can be overridden");
    }

    @Test
    void manualReviewCannotRemainManualReview() {
        AdminOverrideRequest overrideRequest = new AdminOverrideRequest(
                DecisionResult.MANUAL_REVIEW,
                "OVERRIDE_ADMIN_APPROVED",
                null);
        when(accessRequestRepository.findByIdForUpdate(REQUEST_ID))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> manualReviewService.processOverride(
                REQUEST_ID,
                overrideRequest,
                ADMIN_USERNAME))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Override outcome must be APPROVED or REJECTED");

        verify(accessRequestRepository, never())
                .save(any(AccessRequestEntity.class));
        verifyNoInteractions(persistenceService, auditLogService);
    }

    @Test
    void missingRequestReturnsNotFound() {
        AdminOverrideRequest overrideRequest = new AdminOverrideRequest(
                DecisionResult.APPROVED,
                "OVERRIDE_ADMIN_APPROVED",
                null);
        when(accessRequestRepository.findByIdForUpdate(REQUEST_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> manualReviewService.processOverride(
                REQUEST_ID,
                overrideRequest,
                ADMIN_USERNAME))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Access request not found");

        verify(accessRequestRepository, never())
                .save(any(AccessRequestEntity.class));
        verifyNoInteractions(persistenceService, auditLogService);
    }

    private void assertSuccessfulOverride(
            DecisionResult outcome,
            String reasonCode) {

        AdminOverrideRequest overrideRequest = new AdminOverrideRequest(
                outcome,
                reasonCode,
                "Reviewed by admin");
        when(accessRequestRepository.findByIdForUpdate(REQUEST_ID))
                .thenReturn(Optional.of(request));
        when(accessRequestRepository.save(request)).thenReturn(request);
        when(persistenceService.toResponse(request))
                .thenAnswer(invocation -> createResponse(request));

        AccessRequestResponse response = manualReviewService.processOverride(
                REQUEST_ID,
                overrideRequest,
                ADMIN_USERNAME);

        assertThat(request.getOutcome()).isEqualTo(outcome);
        assertThat(request.getReasonCode()).isEqualTo(reasonCode);
        assertThat(response.getId()).isEqualTo(REQUEST_ID);
        assertThat(response.getOutcome()).isEqualTo(outcome);
        assertThat(response.getReasonCode()).isEqualTo(reasonCode);
        assertThat(response.getCreatedAt()).isEqualTo(CREATED_AT);
        verify(accessRequestRepository).save(request);
        verify(persistenceService).toResponse(request);
        verify(auditLogService).logAction(
                REQUEST_ID,
                ADMIN_USERNAME,
                AuditLogService.ADMIN_OVERRIDE,
                DecisionResult.MANUAL_REVIEW.name(),
                outcome.name(),
                "Reviewed by admin");
    }

    private AccessRequestResponse createResponse(AccessRequestEntity entity) {
        return new AccessRequestResponse(
                entity.getId(),
                entity.getAgent().getId(),
                entity.getAgent().getAgentCode(),
                entity.getSymbol(),
                entity.getTradeType(),
                entity.getRequestedVolume(),
                entity.getRiskScore(),
                entity.getOutcome(),
                entity.getReasonCode(),
                entity.getIdempotencyKey(),
                entity.getCreatedAt());
    }
}
