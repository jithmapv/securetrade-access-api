package com.securetrade.accessapi.controller;

import com.securetrade.accessapi.common.exception.DuplicateResourceException;
import com.securetrade.accessapi.common.exception.ResourceNotFoundException;
import com.securetrade.accessapi.dto.request.CreateAgentRequest;
import com.securetrade.accessapi.dto.request.UpdateAgentStatusRequest;
import com.securetrade.accessapi.dto.response.AgentProfileResponse;
import com.securetrade.accessapi.service.TradingAgentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TradingAgentController {

    private final TradingAgentService tradingAgentService;

    public TradingAgentController(TradingAgentService tradingAgentService) {
        this.tradingAgentService = tradingAgentService;
    }

    @PostMapping("/admin/agents")
    public ResponseEntity<AgentProfileResponse> registerAgent(
            @Valid @RequestBody CreateAgentRequest request) {

        AgentProfileResponse response = tradingAgentService.registerAgent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/agents/me")
    public ResponseEntity<AgentProfileResponse> getMyProfile(Authentication authentication) {
        AgentProfileResponse response =
                tradingAgentService.getAgentProfileByUsername(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/agents/{id}")
    public ResponseEntity<AgentProfileResponse> getAgentById(@PathVariable UUID id) {
        return ResponseEntity.ok(tradingAgentService.getAgentProfileById(id));
    }

    @PatchMapping("/admin/agents/{id}/status")
    public ResponseEntity<AgentProfileResponse> updateAgentStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAgentStatusRequest request) {

        AgentProfileResponse response =
                tradingAgentService.updateAgentStatus(id, request.getStatus());
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Void> handleResourceNotFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Void> handleDuplicateResource() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
