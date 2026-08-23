package com.securetrade.accessapi.dto.response;

import java.time.Instant;
import java.util.UUID;

public class AuditLogResponse {

    private final UUID id;
    private final UUID requestId;
    private final String actorUsername;
    private final String action;
    private final String previousState;
    private final String newState;
    private final String details;
    private final Instant timestamp;

    public AuditLogResponse(
            UUID id,
            UUID requestId,
            String actorUsername,
            String action,
            String previousState,
            String newState,
            String details,
            Instant timestamp) {

        this.id = id;
        this.requestId = requestId;
        this.actorUsername = actorUsername;
        this.action = action;
        this.previousState = previousState;
        this.newState = newState;
        this.details = details;
        this.timestamp = timestamp;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getAction() {
        return action;
    }

    public String getPreviousState() {
        return previousState;
    }

    public String getNewState() {
        return newState;
    }

    public String getDetails() {
        return details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
