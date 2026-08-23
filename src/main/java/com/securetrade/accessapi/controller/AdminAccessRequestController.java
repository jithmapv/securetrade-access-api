package com.securetrade.accessapi.controller;

import com.securetrade.accessapi.common.exception.InvalidRequestException;
import com.securetrade.accessapi.common.exception.ResourceNotFoundException;
import com.securetrade.accessapi.dto.request.AdminOverrideRequest;
import com.securetrade.accessapi.dto.response.AccessRequestResponse;
import com.securetrade.accessapi.security.util.SecurityUtils;
import com.securetrade.accessapi.service.ManualReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/requests")
@Tag(name = "Manual Review", description = "Admin decisions for requests in manual review")
@SecurityRequirement(name = "bearerAuth")
public class AdminAccessRequestController {

    private final ManualReviewService manualReviewService;

    public AdminAccessRequestController(ManualReviewService manualReviewService) {
        this.manualReviewService = manualReviewService;
    }

    @PostMapping("/{id}/override")
    @Operation(
            summary = "Override a manual review request",
            description = "Sets a manual review request to approved or rejected. Admin access is required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Override saved"),
            @ApiResponse(responseCode = "400", description = "Request data or state is not valid"),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or not valid"),
            @ApiResponse(responseCode = "403", description = "Admin access is required"),
            @ApiResponse(responseCode = "404", description = "Access request not found")
    })
    public ResponseEntity<AccessRequestResponse> overrideRequest(
            @PathVariable UUID id,
            @Valid @RequestBody AdminOverrideRequest overrideRequest) {

        // Get admin name from security context
        String adminUsername = SecurityUtils.getCurrentUsername();
        AccessRequestResponse response = manualReviewService
                .processOverride(id, overrideRequest, adminUsername);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Void> handleResourceNotFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Void> handleInvalidRequest() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
