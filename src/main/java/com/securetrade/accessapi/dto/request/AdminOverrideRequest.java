package com.securetrade.accessapi.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.securetrade.accessapi.common.enums.DecisionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Admin decision for a request in manual review")
public class AdminOverrideRequest {

    @Schema(
            description = "Final decision for the request",
            example = "APPROVED",
            allowableValues = {"APPROVED", "REJECTED"})
    @NotNull(message = "Outcome is required")
    private DecisionResult outcome;

    @Schema(
            description = "Code that explains the admin decision",
            example = "OVERRIDE_ADMIN_APPROVED",
            maxLength = 32)
    @NotBlank(message = "Reason code is required")
    @Size(max = 32, message = "Reason code must be 32 characters or less")
    private String reasonCode;

    @Schema(
            description = "Optional note from the admin",
            example = "Risk was checked by the compliance team.",
            maxLength = 255)
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
    @Schema(hidden = true)
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
