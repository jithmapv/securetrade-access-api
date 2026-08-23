package com.securetrade.accessapi.dto.response;

import com.securetrade.accessapi.common.enums.DecisionResult;
import com.securetrade.accessapi.common.enums.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Saved trade access decision")
public class AccessRequestResponse {

    @Schema(description = "Access request ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private final UUID id;

    @Schema(description = "Trading agent ID", example = "40b383c1-302f-4bdb-818e-88afdbd61545")
    private final UUID agentId;

    @Schema(description = "Unique agent code", example = "AGENT-001")
    private final String agentCode;

    @Schema(description = "Market symbol", example = "AAPL")
    private final String symbol;

    @Schema(description = "Trade direction", example = "BUY", allowableValues = {"BUY", "SELL"})
    private final TradeType tradeType;

    @Schema(description = "Requested trade volume", example = "500000.00")
    private final BigDecimal requestedVolume;

    @Schema(description = "Risk score used for the decision", example = "0.20")
    private final BigDecimal riskScore;

    @Schema(
            description = "Evaluation result",
            example = "APPROVED",
            allowableValues = {"APPROVED", "REJECTED", "MANUAL_REVIEW"})
    private final DecisionResult outcome;

    @Schema(description = "Code that explains the result", example = "EXEC_PASS_STANDARD")
    private final String reasonCode;

    @Schema(description = "Optional key used to prevent duplicate requests", example = "trade-2026-0001")
    private final String idempotencyKey;

    @Schema(description = "Request creation time", example = "2026-08-23T10:15:30Z")
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
