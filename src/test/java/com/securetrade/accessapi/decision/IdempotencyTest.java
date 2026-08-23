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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyTest {

    private static final UUID AGENT_ID =
            UUID.fromString("c2785827-e4af-4b4e-94ea-bc248eebf3c5");
    private static final UUID FIRST_REQUEST_ID =
            UUID.fromString("81c87e68-4162-49fa-865e-0093b8c6cb79");
    private static final UUID SECOND_REQUEST_ID =
            UUID.fromString("9a5c789d-7824-47aa-a9b5-e12ef5f6e238");
    private static final Instant CREATED_AT = Instant.parse("2026-08-23T11:00:00Z");
    private static final String IDEMPOTENCY_KEY = "trade-request-001";

    @Mock
    private AccessRequestPersistenceService persistenceService;

    private DecisionEngineService decisionEngineService;
    private TradingAgentEntity agent;
    private SubmitTradeAccessRequest request;

    @BeforeEach
    void setUp() {
        decisionEngineService = new DecisionEngineService(persistenceService);

        UserEntity user = new UserEntity(
                "agent.one",
                "password-hash",
                UserRole.TRADING_AGENT,
                AgentStatus.ACTIVE);
        agent = new TradingAgentEntity(
                user,
                "AGT-007",
                "Agent Seven",
                "MOMENTUM",
                new BigDecimal("5000000.00"));
        agent.setId(AGENT_ID);

        request = new SubmitTradeAccessRequest(
                "AAPL",
                TradeType.BUY,
                new BigDecimal("500000.00"),
                new BigDecimal("0.20"));
    }

    @Test
    void newKeyEvaluatesAndSavesRequest() {
        when(persistenceService.findByAgentIdAndIdempotencyKey(
                AGENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(persistenceService.saveIdempotentRequest(any(AccessRequestEntity.class)))
                .thenAnswer(invocation -> savedResponse(
                        invocation.getArgument(0),
                        FIRST_REQUEST_ID,
                        CREATED_AT));

        AccessRequestResponse response = decisionEngineService
                .evaluateAndSave(agent, request, IDEMPOTENCY_KEY);

        assertThat(response.getOutcome()).isEqualTo(DecisionResult.APPROVED);
        assertThat(response.getReasonCode()).isEqualTo(ReasonCode.EXEC_PASS_STANDARD);
        assertThat(response.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);

        ArgumentCaptor<AccessRequestEntity> entityCaptor =
                ArgumentCaptor.forClass(AccessRequestEntity.class);
        verify(persistenceService).saveIdempotentRequest(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getIdempotencyKey())
                .isEqualTo(IDEMPOTENCY_KEY);
        verify(persistenceService, never())
                .saveRequest(any(AccessRequestEntity.class));
    }

    @Test
    void existingKeyReturnsCachedResultWithoutEvaluation() {
        AccessRequestResponse cachedResponse = cachedResponse();
        agent.getUser().setStatus(AgentStatus.SUSPENDED);
        when(persistenceService.findByAgentIdAndIdempotencyKey(
                AGENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(cachedResponse));

        AccessRequestResponse response = decisionEngineService
                .evaluateAndSave(agent, request, IDEMPOTENCY_KEY);

        assertThat(response).isSameAs(cachedResponse);
        assertThat(response.getOutcome()).isEqualTo(DecisionResult.APPROVED);
        verify(persistenceService, never())
                .saveIdempotentRequest(any(AccessRequestEntity.class));
        verify(persistenceService, never())
                .saveRequest(any(AccessRequestEntity.class));
    }

    @Test
    void missingKeyCreatesDistinctRequests() {
        AtomicInteger saveCount = new AtomicInteger();
        when(persistenceService.saveRequest(any(AccessRequestEntity.class)))
                .thenAnswer(invocation -> savedResponse(
                        invocation.getArgument(0),
                        saveCount.getAndIncrement() == 0
                                ? FIRST_REQUEST_ID
                                : SECOND_REQUEST_ID,
                        CREATED_AT.plusSeconds(saveCount.get())));

        AccessRequestResponse first = decisionEngineService
                .evaluateAndSave(agent, request, null);
        AccessRequestResponse second = decisionEngineService
                .evaluateAndSave(agent, request, null);

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getIdempotencyKey()).isNull();
        assertThat(second.getIdempotencyKey()).isNull();
        verify(persistenceService, times(2))
                .saveRequest(any(AccessRequestEntity.class));
        verify(persistenceService, never())
                .findByAgentIdAndIdempotencyKey(
                        any(UUID.class), any(String.class));
    }

    @Test
    void blankKeyIsTreatedAsMissing() {
        when(persistenceService.saveRequest(any(AccessRequestEntity.class)))
                .thenAnswer(invocation -> savedResponse(
                        invocation.getArgument(0),
                        FIRST_REQUEST_ID,
                        CREATED_AT));

        AccessRequestResponse response = decisionEngineService
                .evaluateAndSave(agent, request, "   ");

        assertThat(response.getIdempotencyKey()).isNull();
        verify(persistenceService, never())
                .findByAgentIdAndIdempotencyKey(
                        any(UUID.class), any(String.class));
        verify(persistenceService, never())
                .saveIdempotentRequest(any(AccessRequestEntity.class));
    }

    @Test
    void uniqueConflictReturnsSavedWinner() {
        AccessRequestResponse cachedResponse = cachedResponse();
        DataIntegrityViolationException conflict =
                new DataIntegrityViolationException("Duplicate key");
        when(persistenceService.findByAgentIdAndIdempotencyKey(
                AGENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(cachedResponse));
        when(persistenceService.saveIdempotentRequest(any(AccessRequestEntity.class)))
                .thenThrow(conflict);

        AccessRequestResponse response = decisionEngineService
                .evaluateAndSave(agent, request, IDEMPOTENCY_KEY);

        assertThat(response).isSameAs(cachedResponse);
        verify(persistenceService, times(2))
                .findByAgentIdAndIdempotencyKey(AGENT_ID, IDEMPOTENCY_KEY);
    }

    @Test
    void uniqueConflictWithoutSavedWinnerRethrowsError() {
        DataIntegrityViolationException conflict =
                new DataIntegrityViolationException("Database error");
        when(persistenceService.findByAgentIdAndIdempotencyKey(
                AGENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(persistenceService.saveIdempotentRequest(any(AccessRequestEntity.class)))
                .thenThrow(conflict);

        assertThatThrownBy(() -> decisionEngineService
                .evaluateAndSave(agent, request, IDEMPOTENCY_KEY))
                .isSameAs(conflict);
    }

    private AccessRequestResponse savedResponse(
            AccessRequestEntity entity,
            UUID requestId,
            Instant createdAt) {

        entity.setId(requestId);
        entity.setCreatedAt(createdAt);
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

    private AccessRequestResponse cachedResponse() {
        return new AccessRequestResponse(
                FIRST_REQUEST_ID,
                AGENT_ID,
                agent.getAgentCode(),
                "MSFT",
                TradeType.SELL,
                new BigDecimal("750000.00"),
                new BigDecimal("0.25"),
                DecisionResult.APPROVED,
                ReasonCode.EXEC_PASS_STANDARD,
                IDEMPOTENCY_KEY,
                CREATED_AT);
    }
}
