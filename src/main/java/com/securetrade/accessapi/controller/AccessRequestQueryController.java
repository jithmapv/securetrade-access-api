package com.securetrade.accessapi.controller;

import com.securetrade.accessapi.common.exception.ResourceNotFoundException;
import com.securetrade.accessapi.dto.response.AccessRequestResponse;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.repository.AccessRequestRepository;
import com.securetrade.accessapi.repository.TradingAgentRepository;
import com.securetrade.accessapi.security.service.OwnershipValidationService;
import com.securetrade.accessapi.security.util.SecurityUtils;
import com.securetrade.accessapi.service.AccessRequestPersistenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access/requests")
@Tag(name = "Access Request History", description = "Saved trade access request operations")
@SecurityRequirement(name = "bearerAuth")
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
    @Operation(summary = "Get my access requests", description = "Returns a page of requests for the current agent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access request page returned"),
            @ApiResponse(responseCode = "400", description = "Page values are not valid"),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or not valid"),
            @ApiResponse(responseCode = "403", description = "Access is not allowed"),
            @ApiResponse(responseCode = "404", description = "Trading agent not found")
    })
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
    @Operation(
            summary = "Get an access request",
            description = "Returns one request. Agents may only view their own requests. Admins may view any request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access request returned"),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or not valid"),
            @ApiResponse(responseCode = "403", description = "The user does not own this request"),
            @ApiResponse(responseCode = "404", description = "Access request not found")
    })
    public ResponseEntity<AccessRequestResponse> getRequest(@PathVariable UUID id) {
        String username = SecurityUtils.getCurrentUsername();

        // Get request and owner from database
        AccessRequestEntity request = accessRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Access request not found"));

        // Check if current user owns the request
        ownershipValidationService.validateRequestOwnership(request, username);

        return ResponseEntity.ok(persistenceService.toResponse(request));
    }
}
