package com.securetrade.accessapi.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Compliance audit record")
public class AuditLogResponse {

    @Schema(description = "Audit record ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private final UUID id;

    @Schema(description = "Related access request ID, when present")
    private final UUID requestId;

    @Schema(description = "Username that caused the action", example = "admin.user")
    private final String actorUsername;

    @Schema(description = "Recorded action", example = "ADMIN_OVERRIDE")
    private final String action;

    @Schema(description = "State before the action", example = "MANUAL_REVIEW")
    private final String previousState;

    @Schema(description = "State after the action", example = "APPROVED")
    private final String newState;

    @Schema(description = "Short details about the action", example = "Risk was checked.")
    private final String details;

    @Schema(description = "Time the action was recorded", example = "2026-08-23T10:15:30Z")
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
