package com.securetrade.accessapi.controller;

import com.securetrade.accessapi.common.exception.DuplicateResourceException;
import com.securetrade.accessapi.common.exception.ResourceNotFoundException;
import com.securetrade.accessapi.dto.request.CreateAgentRequest;
import com.securetrade.accessapi.dto.request.UpdateAgentStatusRequest;
import com.securetrade.accessapi.dto.response.AgentProfileResponse;
import com.securetrade.accessapi.security.util.SecurityUtils;
import com.securetrade.accessapi.service.TradingAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Trading Agents", description = "Trading agent account and profile operations")
@SecurityRequirement(name = "bearerAuth")
public class TradingAgentController {

    private final TradingAgentService tradingAgentService;

    public TradingAgentController(TradingAgentService tradingAgentService) {
        this.tradingAgentService = tradingAgentService;
    }

    @PostMapping("/admin/agents")
    @Operation(summary = "Register a trading agent", description = "Creates an agent account and profile. Admin access is required.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Agent created"),
            @ApiResponse(responseCode = "400", description = "Request data is not valid"),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or not valid"),
            @ApiResponse(responseCode = "403", description = "Admin access is required"),
            @ApiResponse(responseCode = "409", description = "Username or agent code already exists")
    })
    public ResponseEntity<AgentProfileResponse> registerAgent(
            @Valid @RequestBody CreateAgentRequest request) {

        AgentProfileResponse response = tradingAgentService.registerAgent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/agents/me")
    @Operation(summary = "Get my agent profile", description = "Returns the profile linked to the current user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agent profile returned"),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or not valid"),
            @ApiResponse(responseCode = "403", description = "Access is not allowed"),
            @ApiResponse(responseCode = "404", description = "Agent profile not found")
    })
    public ResponseEntity<AgentProfileResponse> getMyProfile(Authentication authentication) {
        AgentProfileResponse response =
                tradingAgentService.getAgentProfileByUsername(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/agents/{id}")
    @Operation(summary = "Get an agent profile", description = "Returns an agent profile by ID. Admin access is required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agent profile returned"),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or not valid"),
            @ApiResponse(responseCode = "403", description = "Admin access is required"),
            @ApiResponse(responseCode = "404", description = "Agent profile not found")
    })
    public ResponseEntity<AgentProfileResponse> getAgentById(@PathVariable UUID id) {
        return ResponseEntity.ok(tradingAgentService.getAgentProfileById(id));
    }

    @PatchMapping("/admin/agents/{id}/status")
    @Operation(summary = "Update an agent status", description = "Changes an agent account status. Admin access is required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agent status updated"),
            @ApiResponse(responseCode = "400", description = "Request data is not valid"),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or not valid"),
            @ApiResponse(responseCode = "403", description = "Admin access is required"),
            @ApiResponse(responseCode = "404", description = "Agent profile not found")
    })
    public ResponseEntity<AgentProfileResponse> updateAgentStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAgentStatusRequest request) {

        AgentProfileResponse response =
                tradingAgentService.updateAgentStatus(
                        id,
                        request.getStatus(),
                        SecurityUtils.getCurrentUsername());
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
