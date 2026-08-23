package com.securetrade.accessapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trading_agents")
public class TradingAgentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_trading_agents_user"))
    private UserEntity user;

    @Column(name = "agent_code", nullable = false, unique = true, length = 50)
    private String agentCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "strategy_type", nullable = false, length = 50)
    private String strategyType;

    @Column(name = "max_allowed_volume", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxAllowedVolume;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public TradingAgentEntity() {
    }

    public TradingAgentEntity(
            UserEntity user,
            String agentCode,
            String name,
            String strategyType,
            BigDecimal maxAllowedVolume) {

        this.user = user;
        this.agentCode = agentCode;
        this.name = name;
        this.strategyType = strategyType;
        this.maxAllowedVolume = maxAllowedVolume;
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

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStrategyType() {
        return strategyType;
    }

    public void setStrategyType(String strategyType) {
        this.strategyType = strategyType;
    }

    public BigDecimal getMaxAllowedVolume() {
        return maxAllowedVolume;
    }

    public void setMaxAllowedVolume(BigDecimal maxAllowedVolume) {
        this.maxAllowedVolume = maxAllowedVolume;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
