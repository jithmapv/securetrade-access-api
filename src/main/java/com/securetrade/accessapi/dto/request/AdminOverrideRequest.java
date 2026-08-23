package com.securetrade.accessapi.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.securetrade.accessapi.common.enums.DecisionResult;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AdminOverrideRequest {

    @NotNull(message = "Outcome is required")
    private DecisionResult outcome;

    @NotBlank(message = "Reason code is required")
    @Size(max = 32, message = "Reason code must be 32 characters or less")
    private String reasonCode;

    @Size(max = 255, message = "Admin notes must be 255 characters or less")
    private String adminNotes;

    public AdminOverrideRequest() {
    }

    public AdminOverrideRequest(
            DecisionResult outcome,
            String reasonCode,
            String adminNotes) {

        this.outcome = outcome;
        this.reasonCode = reasonCode;
        this.adminNotes = adminNotes;
    }

    @JsonIgnore
    @AssertFalse(message = "Override outcome must be APPROVED or REJECTED")
    public boolean isManualReviewOutcome() {
        return outcome == DecisionResult.MANUAL_REVIEW;
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

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }
}
