package com.securetrade.accessapi.decision;

import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.DecisionResult;
import com.securetrade.accessapi.dto.request.SubmitTradeAccessRequest;
import com.securetrade.accessapi.dto.response.AccessRequestResponse;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.service.AccessRequestPersistenceService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
public class DecisionEngineService {

    private static final BigDecimal HARD_VOLUME_LIMIT =
            new BigDecimal("10000000.00");
    private static final BigDecimal MANUAL_VOLUME_LIMIT =
            new BigDecimal("1000000.00");
    private static final BigDecimal CRITICAL_RISK_LIMIT =
            new BigDecimal("0.80");
    private static final BigDecimal MANUAL_RISK_LIMIT =
            new BigDecimal("0.30");

    private final AccessRequestPersistenceService persistenceService;

    public DecisionEngineService(AccessRequestPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public AccessRequestResponse evaluateAndSave(
            TradingAgentEntity agent,
            SubmitTradeAccessRequest request,
            String idempotencyKey) {

        String effectiveIdempotencyKey = StringUtils.hasText(idempotencyKey)
                ? idempotencyKey
                : null;

        if (effectiveIdempotencyKey != null) {
            // Return saved result for duplicate request
            AccessRequestResponse savedResponse = persistenceService
                    .findByAgentIdAndIdempotencyKey(
                            agent.getId(),
                            effectiveIdempotencyKey)
                    .orElse(null);

            if (savedResponse != null) {
                return savedResponse;
            }
        }

        // Check if agent account is active
        if (agent.getUser().getStatus() != AgentStatus.ACTIVE) {
            return saveDecision(
                    agent,
                    request,
                    effectiveIdempotencyKey,
                    DecisionResult.REJECTED,
                    ReasonCode.ERR_AGENT_SUSPENDED);
        }

        // Check agent volume limit
        if (isGreaterThan(request.getRequestedVolume(), agent.getMaxAllowedVolume())) {
            return saveDecision(
                    agent,
                    request,
                    effectiveIdempotencyKey,
                    DecisionResult.REJECTED,
                    ReasonCode.ERR_EXCEEDS_AGENT_LIMIT);
        }

        // Check hard limits
        if (isGreaterThan(request.getRequestedVolume(), HARD_VOLUME_LIMIT)
                || isGreaterThan(request.getRiskScore(), CRITICAL_RISK_LIMIT)) {
            return saveDecision(
                    agent,
                    request,
                    effectiveIdempotencyKey,
                    DecisionResult.REJECTED,
                    ReasonCode.ERR_EXCEEDS_HARD_LIMIT);
        }

        // Check manual review limits
        if (isGreaterThan(request.getRequestedVolume(), MANUAL_VOLUME_LIMIT)
                || isGreaterThan(request.getRiskScore(), MANUAL_RISK_LIMIT)) {
            return saveDecision(
                    agent,
                    request,
                    effectiveIdempotencyKey,
                    DecisionResult.MANUAL_REVIEW,
                    ReasonCode.FLAG_HIGH_VOL_RISK);
        }

        // Approve standard trade request
        return saveDecision(
                agent,
                request,
                effectiveIdempotencyKey,
                DecisionResult.APPROVED,
                ReasonCode.EXEC_PASS_STANDARD);
    }

    private boolean isGreaterThan(BigDecimal value, BigDecimal limit) {
        return value.compareTo(limit) > 0;
    }

    private AccessRequestResponse saveDecision(
            TradingAgentEntity agent,
            SubmitTradeAccessRequest request,
            String idempotencyKey,
            DecisionResult outcome,
            String reasonCode) {

        AccessRequestEntity entity = new AccessRequestEntity(
                agent,
                request.getSymbol(),
                request.getTradeType(),
                request.getRequestedVolume(),
                request.getRiskScore(),
                outcome,
                reasonCode,
                idempotencyKey);

        String actorUsername = agent.getUser().getUsername();

        if (!StringUtils.hasText(idempotencyKey)) {
            return persistenceService.saveRequest(entity, actorUsername);
        }

        try {
            // Save new request with idempotency key
            return persistenceService.saveIdempotentRequest(entity, actorUsername);
        } catch (DataIntegrityViolationException exception) {
            // Return request saved by another call
            return persistenceService
                    .findByAgentIdAndIdempotencyKey(agent.getId(), idempotencyKey)
                    .orElseThrow(() -> exception);
        }
    }
}
