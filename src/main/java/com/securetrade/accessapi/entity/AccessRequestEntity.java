package com.securetrade.accessapi.entity;

import com.securetrade.accessapi.common.enums.DecisionResult;
import com.securetrade.accessapi.common.enums.TradeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "access_requests",
        indexes = @Index(
                name = "idx_access_requests_agent_id",
                columnList = "agent_id"))
public class AccessRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "agent_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_access_requests_agent"))
    private TradingAgentEntity agent;

    @Column(nullable = false, length = 12)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false, length = 4)
    private TradeType tradeType;

    @Column(name = "requested_volume", nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedVolume;

    @Column(name = "risk_score", nullable = false, precision = 3, scale = 2)
    private BigDecimal riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DecisionResult outcome;

    @Column(name = "reason_code", nullable = false, length = 32)
    private String reasonCode;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AccessRequestEntity() {
    }

    public AccessRequestEntity(
            TradingAgentEntity agent,
            String symbol,
            TradeType tradeType,
            BigDecimal requestedVolume,
            BigDecimal riskScore,
            DecisionResult outcome,
            String reasonCode,
            String idempotencyKey) {

        this.agent = agent;
        this.symbol = symbol;
        this.tradeType = tradeType;
        this.requestedVolume = requestedVolume;
        this.riskScore = riskScore;
        this.outcome = outcome;
        this.reasonCode = reasonCode;
        this.idempotencyKey = idempotencyKey;
    }

    // Set the create time before save
    @PrePersist
    void setCreateTime() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public TradingAgentEntity getAgent() {
        return agent;
    }

    public void setAgent(TradingAgentEntity agent) {
        this.agent = agent;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public TradeType getTradeType() {
        return tradeType;
    }

    public void setTradeType(TradeType tradeType) {
        this.tradeType = tradeType;
    }

    public BigDecimal getRequestedVolume() {
        return requestedVolume;
    }

    public void setRequestedVolume(BigDecimal requestedVolume) {
        this.requestedVolume = requestedVolume;
    }

    public BigDecimal getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(BigDecimal riskScore) {
        this.riskScore = riskScore;
    }

    public DecisionResult getOutcome() {
        return outcome;
    }

    public void setOutcome(DecisionResult outcome) {
        this.outcome = outcome;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
