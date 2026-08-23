package com.securetrade.accessapi.controller;

import com.securetrade.accessapi.common.exception.ResourceNotFoundException;
import com.securetrade.accessapi.common.exception.SecureTradeAccessDeniedException;
import com.securetrade.accessapi.dto.response.AccessRequestResponse;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.repository.AccessRequestRepository;
import com.securetrade.accessapi.repository.TradingAgentRepository;
import com.securetrade.accessapi.security.service.OwnershipValidationService;
import com.securetrade.accessapi.security.util.SecurityUtils;
import com.securetrade.accessapi.service.AccessRequestPersistenceService;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access/requests")
public class AccessRequestQueryController {

    private final TradingAgentRepository tradingAgentRepository;
    private final AccessRequestRepository accessRequestRepository;
    private final AccessRequestPersistenceService persistenceService;
    private final OwnershipValidationService ownershipValidationService;

    public AccessRequestQueryController(
            TradingAgentRepository tradingAgentRepository,
            AccessRequestRepository accessRequestRepository,
            AccessRequestPersistenceService persistenceService,
            OwnershipValidationService ownershipValidationService) {

        this.tradingAgentRepository = tradingAgentRepository;
        this.accessRequestRepository = accessRequestRepository;
        this.persistenceService = persistenceService;
        this.ownershipValidationService = ownershipValidationService;
    }

    @GetMapping("/me")
    public ResponseEntity<Page<AccessRequestResponse>> getMyRequests(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size) {

        // Get authenticated agent
        String username = SecurityUtils.getCurrentUsername();
        TradingAgentEntity agent = tradingAgentRepository.findByUserUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Trading agent not found"));

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        return ResponseEntity.ok(
                persistenceService.getRequestsByAgent(agent.getId(), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccessRequestResponse> getRequest(@PathVariable UUID id) {
        String username = SecurityUtils.getCurrentUsername();

        // Get request and owner from database
        AccessRequestEntity request = accessRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Access request not found"));

        // Check if current user owns the request
        ownershipValidationService.validateRequestOwnership(request, username);

        return ResponseEntity.ok(persistenceService.toResponse(request));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Void> handleResourceNotFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(SecureTradeAccessDeniedException.class)
    public ResponseEntity<Void> handleAccessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
