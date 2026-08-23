package com.securetrade.accessapi.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Schema(description = "Standard API error response")
public class ApiErrorResponse {

    @Schema(
            description = "Link that describes the error type",
            example = "https://api.securetrade.com/errors/resource-not-found")
    private final String type;

    @Schema(description = "Short error name", example = "Resource Not Found")
    private final String title;

    @Schema(description = "HTTP status code", example = "404")
    private final int status;

    @Schema(description = "Clear error message", example = "Agent profile not found")
    private final String detail;

    @Schema(description = "Request path where the error happened", example = "/api/v1/admin/agents/123")
    private final String instance;

    @Schema(description = "Application error code", example = "RESOURCE_NOT_FOUND")
    private final String errorCode;

    @Schema(description = "Time when the error happened", example = "2026-08-23T10:15:30Z")
    private final Instant timestamp;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Validation errors for request fields")
    private final Map<String, String> validationErrors;

    public ApiErrorResponse(
            String type,
            String title,
            int status,
            String detail,
            String instance,
            String errorCode,
            Instant timestamp,
            Map<String, String> validationErrors) {

        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.errorCode = errorCode;
        this.timestamp = timestamp;
        this.validationErrors = copyValidationErrors(validationErrors);
    }

    private Map<String, String> copyValidationErrors(
            Map<String, String> validationErrors) {

        if (validationErrors == null || validationErrors.isEmpty()) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(new LinkedHashMap<>(validationErrors));
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public int getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public String getInstance() {
        return instance;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, String> getValidationErrors() {
        return validationErrors;
    }
}
