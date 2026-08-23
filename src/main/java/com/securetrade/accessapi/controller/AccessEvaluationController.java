package com.securetrade.accessapi.controller;

import com.securetrade.accessapi.common.exception.InvalidRequestException;
import com.securetrade.accessapi.common.exception.ResourceNotFoundException;
import com.securetrade.accessapi.decision.DecisionEngineService;
import com.securetrade.accessapi.dto.request.SubmitTradeAccessRequest;
import com.securetrade.accessapi.dto.response.AccessRequestResponse;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.repository.TradingAgentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/access")
@Tag(name = "Trade Evaluation", description = "Trade access decision operations")
@SecurityRequirement(name = "bearerAuth")
public class AccessEvaluationController {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private final TradingAgentRepository tradingAgentRepository;
    private final DecisionEngineService decisionEngineService;

    public AccessEvaluationController(
            TradingAgentRepository tradingAgentRepository,
            DecisionEngineService decisionEngineService) {

        this.tradingAgentRepository = tradingAgentRepository;
        this.decisionEngineService = decisionEngineService;
    }

    @PostMapping("/evaluate")
    @Operation(
            summary = "Evaluate a trade request",
            description = "Checks a trade request and saves the decision. An idempotency key is optional.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trade request evaluated"),
            @ApiResponse(responseCode = "400", description = "Request data or idempotency key is not valid"),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or not valid"),
            @ApiResponse(responseCode = "403", description = "Access is not allowed"),
            @ApiResponse(responseCode = "404", description = "Trading agent not found")
    })
    public ResponseEntity<AccessRequestResponse> evaluate(
            @Valid @RequestBody SubmitTradeAccessRequest request,
            @Parameter(
                    description = "Optional key used to return the same saved result",
                    example = "trade-2026-0001")
            @RequestHeader(
                    value = "X-Idempotency-Key",
                    required = false) String rawIdempotencyKey) {

        // Get username from security context
        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        // Get agent and user status from database
        TradingAgentEntity agent = tradingAgentRepository.findByUserUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Trading agent not found"));

        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        AccessRequestResponse response = decisionEngineService
                .evaluateAndSave(agent, request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    private String normalizeIdempotencyKey(String rawIdempotencyKey) {
        if (rawIdempotencyKey == null) {
            return null;
        }

        // Remove spaces around the key
        String idempotencyKey = rawIdempotencyKey.trim();
        if (idempotencyKey.isEmpty()) {
            return null;
        }

        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new InvalidRequestException(
                    "Idempotency key must be 64 characters or less");
        }

        return idempotencyKey;
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
