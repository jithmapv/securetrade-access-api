package com.securetrade.accessapi.dto.request;

import com.securetrade.accessapi.common.enums.AgentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "New status for a trading agent")
public class UpdateAgentStatusRequest {

    @Schema(
            description = "Agent account status",
            example = "SUSPENDED",
            allowableValues = {"ACTIVE", "INACTIVE", "SUSPENDED"})
    @NotNull(message = "Status is required")
    private AgentStatus status;

    public UpdateAgentStatusRequest() {
    }

    public UpdateAgentStatusRequest(AgentStatus status) {
        this.status = status;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public void setStatus(AgentStatus status) {
        this.status = status;
    }
}
