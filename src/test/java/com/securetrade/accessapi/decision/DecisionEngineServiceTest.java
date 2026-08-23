package com.securetrade.accessapi.decision;

import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.DecisionResult;
import com.securetrade.accessapi.common.enums.TradeType;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.dto.request.SubmitTradeAccessRequest;
import com.securetrade.accessapi.dto.response.AccessRequestResponse;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.entity.UserEntity;
import com.securetrade.accessapi.service.AccessRequestPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecisionEngineServiceTest {

    private static final UUID AGENT_ID =
            UUID.fromString("9fa5ecb8-f344-4cf3-982c-8039965650c6");
    private static final UUID REQUEST_ID =
            UUID.fromString("d9b4bfea-9a03-4afc-a3e1-f4b8b6936397");
    private static final Instant CREATED_AT = Instant.parse("2026-08-23T10:00:00Z");

    @Mock
    private AccessRequestPersistenceService persistenceService;

    @InjectMocks
    private DecisionEngineService decisionEngineService;

    @BeforeEach
    void setUp() {
        when(persistenceService.saveRequest(
                any(AccessRequestEntity.class),
                eq("agent.one")))
                .thenAnswer(invocation -> {
                    AccessRequestEntity entity = invocation.getArgument(0);
                    entity.setId(REQUEST_ID);
                    entity.setCreatedAt(CREATED_AT);
                    return toResponse(entity);
                });
    }

    @Test
    void suspendedAgentIsRejectedFirst() {
        assertDecision(
                AgentStatus.SUSPENDED,
                "1000000.00",
                "11000000.00",
                "0.90",
                DecisionResult.REJECTED,
                ReasonCode.ERR_AGENT_SUSPENDED);
    }

    @Test
    void inactiveAgentIsRejected() {
        assertDecision(
                AgentStatus.INACTIVE,
                "20000000.00",
                "500000.00",
                "0.20",
                DecisionResult.REJECTED,
                ReasonCode.ERR_AGENT_SUSPENDED);
    }

    @Test
    void volumeAboveAgentLimitIsRejectedBeforeHardLimit() {
        assertDecision(
                AgentStatus.ACTIVE,
                "5000000.00",
                "11000000.00",
                "0.20",
                DecisionResult.REJECTED,
                ReasonCode.ERR_EXCEEDS_AGENT_LIMIT);
    }

    @Test
    void volumeAboveHardLimitIsRejected() {
        assertDecision(
                AgentStatus.ACTIVE,
                "20000000.00",
                "10000000.01",
                "0.20",
                DecisionResult.REJECTED,
                ReasonCode.ERR_EXCEEDS_HARD_LIMIT);
    }

    @Test
    void riskAboveCriticalLimitIsRejected() {
        assertDecision(
                AgentStatus.ACTIVE,
                "20000000.00",
                "500000.00",
                "0.81",
                DecisionResult.REJECTED,
                ReasonCode.ERR_EXCEEDS_HARD_LIMIT);
    }

    @Test
    void highVolumeNeedsManualReview() {
        assertDecision(
                AgentStatus.ACTIVE,
                "20000000.00",
                "1000000.01",
                "0.20",
                DecisionResult.MANUAL_REVIEW,
                ReasonCode.FLAG_HIGH_VOL_RISK);
    }

    @Test
    void mediumRiskNeedsManualReview() {
        assertDecision(
                AgentStatus.ACTIVE,
                "20000000.00",
                "500000.00",
                "0.50",
                DecisionResult.MANUAL_REVIEW,
                ReasonCode.FLAG_HIGH_VOL_RISK);
    }

    @Test
    void standardTradeIsApproved() {
        assertDecision(
                AgentStatus.ACTIVE,
                "20000000.00",
                "500000.00",
                "0.20",
                DecisionResult.APPROVED,
                ReasonCode.EXEC_PASS_STANDARD);
    }

    @Test
    void standardBoundariesAreApproved() {
        assertDecision(
                AgentStatus.ACTIVE,
                "1000000.00",
                "1000000.00",
                "0.30",
                DecisionResult.APPROVED,
                ReasonCode.EXEC_PASS_STANDARD);
    }

    @Test
    void hardLimitBoundariesNeedManualReview() {
        assertDecision(
                AgentStatus.ACTIVE,
                "20000000.00",
                "10000000.00",
                "0.80",
                DecisionResult.MANUAL_REVIEW,
                ReasonCode.FLAG_HIGH_VOL_RISK);
    }

    private void assertDecision(
            AgentStatus status,
            String agentLimit,
            String requestedVolume,
            String riskScore,
            DecisionResult expectedOutcome,
            String expectedReasonCode) {

        UserEntity user = new UserEntity(
                "agent.one",
                "password-hash",
                UserRole.TRADING_AGENT,
                status);
        TradingAgentEntity agent = new TradingAgentEntity(
                user,
                "AGT-005",
                "Agent Five",
                "MOMENTUM",
                new BigDecimal(agentLimit));
        agent.setId(AGENT_ID);

        SubmitTradeAccessRequest request = new SubmitTradeAccessRequest(
                "AAPL",
                TradeType.BUY,
                new BigDecimal(requestedVolume),
                new BigDecimal(riskScore));

        AccessRequestResponse response =
                decisionEngineService.evaluateAndSave(agent, request, null);

        assertThat(response.getOutcome()).isEqualTo(expectedOutcome);
        assertThat(response.getReasonCode()).isEqualTo(expectedReasonCode);

        ArgumentCaptor<AccessRequestEntity> entityCaptor =
                ArgumentCaptor.forClass(AccessRequestEntity.class);
        verify(persistenceService).saveRequest(
                entityCaptor.capture(),
                eq("agent.one"));
        AccessRequestEntity savedEntity = entityCaptor.getValue();

        assertThat(savedEntity.getAgent()).isSameAs(agent);
        assertThat(savedEntity.getSymbol()).isEqualTo("AAPL");
        assertThat(savedEntity.getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(savedEntity.getRequestedVolume())
                .isEqualByComparingTo(requestedVolume);
        assertThat(savedEntity.getRiskScore()).isEqualByComparingTo(riskScore);
        assertThat(savedEntity.getOutcome()).isEqualTo(expectedOutcome);
        assertThat(savedEntity.getReasonCode()).isEqualTo(expectedReasonCode);
        assertThat(savedEntity.getIdempotencyKey()).isNull();
    }

    private AccessRequestResponse toResponse(AccessRequestEntity entity) {
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
