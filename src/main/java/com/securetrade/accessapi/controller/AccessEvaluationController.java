package com.securetrade.accessapi.controller;

import com.securetrade.accessapi.common.exception.ResourceNotFoundException;
import com.securetrade.accessapi.decision.DecisionEngineService;
import com.securetrade.accessapi.dto.request.SubmitTradeAccessRequest;
import com.securetrade.accessapi.dto.response.AccessRequestResponse;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.repository.TradingAgentRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/access")
public class AccessEvaluationController {

    private final TradingAgentRepository tradingAgentRepository;
    private final DecisionEngineService decisionEngineService;

    public AccessEvaluationController(
            TradingAgentRepository tradingAgentRepository,
            DecisionEngineService decisionEngineService) {

        this.tradingAgentRepository = tradingAgentRepository;
        this.decisionEngineService = decisionEngineService;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<AccessRequestResponse> evaluate(
            @Valid @RequestBody SubmitTradeAccessRequest request) {

        // Get username from security context
        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        // Get agent and user status from database
        TradingAgentEntity agent = tradingAgentRepository.findByUserUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Trading agent not found"));

        AccessRequestResponse response =
                decisionEngineService.evaluateAndSave(agent, request, null);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Void> handleResourceNotFound() {
        return ResponseEntity.notFound().build();
    }
}
