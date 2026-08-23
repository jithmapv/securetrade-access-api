package com.securetrade.accessapi.controller;

import com.securetrade.accessapi.common.exception.InvalidRequestException;
import com.securetrade.accessapi.common.exception.ResourceNotFoundException;
import com.securetrade.accessapi.dto.request.AdminOverrideRequest;
import com.securetrade.accessapi.dto.response.AccessRequestResponse;
import com.securetrade.accessapi.security.util.SecurityUtils;
import com.securetrade.accessapi.service.ManualReviewService;
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
public class AdminAccessRequestController {

    private final ManualReviewService manualReviewService;

    public AdminAccessRequestController(ManualReviewService manualReviewService) {
        this.manualReviewService = manualReviewService;
    }

    @PostMapping("/{id}/override")
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
