package com.securetrade.accessapi.dto.request;

import com.securetrade.accessapi.common.enums.AgentStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateAgentStatusRequest {

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
