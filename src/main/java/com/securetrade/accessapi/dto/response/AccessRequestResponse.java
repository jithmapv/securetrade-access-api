package com.securetrade.accessapi.dto.response;

import com.securetrade.accessapi.common.enums.DecisionResult;
import com.securetrade.accessapi.common.enums.TradeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class AccessRequestResponse {

    private final UUID id;
    private final UUID agentId;
    private final String agentCode;
    private final String symbol;
    private final TradeType tradeType;
    private final BigDecimal requestedVolume;
    private final BigDecimal riskScore;
    private final DecisionResult outcome;
    private final String reasonCode;
    private final String idempotencyKey;
    private final Instant createdAt;

    public AccessRequestResponse(
            UUID id,
            UUID agentId,
            String agentCode,
            String symbol,
            TradeType tradeType,
            BigDecimal requestedVolume,
            BigDecimal riskScore,
            DecisionResult outcome,
            String reasonCode,
            String idempotencyKey,
            Instant createdAt) {

        this.id = id;
        this.agentId = agentId;
        this.agentCode = agentCode;
        this.symbol = symbol;
        this.tradeType = tradeType;
        this.requestedVolume = requestedVolume;
        this.riskScore = riskScore;
        this.outcome = outcome;
        this.reasonCode = reasonCode;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAgentId() {
        return agentId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getSymbol() {
        return symbol;
    }

    public TradeType getTradeType() {
        return tradeType;
    }

    public BigDecimal getRequestedVolume() {
        return requestedVolume;
    }

    public BigDecimal getRiskScore() {
        return riskScore;
    }

    public DecisionResult getOutcome() {
        return outcome;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
