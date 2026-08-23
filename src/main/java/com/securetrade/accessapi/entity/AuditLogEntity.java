package com.securetrade.accessapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "request_id", updatable = false)
    private UUID requestId;

    @Column(name = "actor_username", nullable = false, updatable = false, length = 50)
    private String actorUsername;

    @Column(nullable = false, updatable = false, length = 50)
    private String action;

    @Column(name = "previous_state", updatable = false, length = 50)
    private String previousState;

    @Column(name = "new_state", nullable = false, updatable = false, length = 50)
    private String newState;

    @Column(updatable = false, length = 255)
    private String details;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    public AuditLogEntity() {
    }

    public AuditLogEntity(
            UUID requestId,
            String actorUsername,
            String action,
            String previousState,
            String newState,
            String details) {

        this.requestId = requestId;
        this.actorUsername = actorUsername;
        this.action = action;
        this.previousState = previousState;
        this.newState = newState;
        this.details = details;
    }

    // Set the audit time before save
    @PrePersist
    void setAuditTime() {
        if (timestamp == null) {
            timestamp = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getPreviousState() {
        return previousState;
    }

    public void setPreviousState(String previousState) {
        this.previousState = previousState;
    }

    public String getNewState() {
        return newState;
    }

    public void setNewState(String newState) {
        this.newState = newState;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
