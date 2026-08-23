package com.securetrade.accessapi.controller;

import com.securetrade.accessapi.common.exception.InvalidRequestException;
import com.securetrade.accessapi.dto.response.AuditLogResponse;
import com.securetrade.accessapi.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Tag(name = "Audit Logs", description = "Compliance audit history operations")
@SecurityRequirement(name = "bearerAuth")
public class AuditLogController {

    private static final int MAX_ACTOR_USERNAME_LENGTH = 50;

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @Operation(
            summary = "Get audit logs",
            description = "Returns audit history. Results can be filtered by actor username. Admin access is required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit log page returned"),
            @ApiResponse(responseCode = "400", description = "Query values are not valid"),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or not valid"),
            @ApiResponse(responseCode = "403", description = "Admin access is required")
    })
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String actorUsername) {

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id")));
        String normalizedActor = normalizeActorUsername(actorUsername);

        Page<AuditLogResponse> response = normalizedActor == null
                ? auditLogService.getAuditLogs(pageable)
                : auditLogService.getAuditLogsByActor(normalizedActor, pageable);
        return ResponseEntity.ok(response);
    }

    private String normalizeActorUsername(String actorUsername) {
        if (actorUsername == null) {
            return null;
        }

        // Remove spaces around the username
        String normalizedActor = actorUsername.trim();
        if (normalizedActor.isEmpty()) {
            return null;
        }

        if (normalizedActor.length() > MAX_ACTOR_USERNAME_LENGTH) {
            throw new InvalidRequestException(
                    "Actor username must be 50 characters or less");
        }

        return normalizedActor;
    }
}
