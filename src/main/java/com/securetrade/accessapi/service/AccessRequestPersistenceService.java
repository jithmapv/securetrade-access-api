package com.securetrade.accessapi.service;

import com.securetrade.accessapi.common.exception.ResourceNotFoundException;
import com.securetrade.accessapi.dto.response.AccessRequestResponse;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.repository.AccessRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccessRequestPersistenceService {

    private final AccessRequestRepository accessRequestRepository;

    public AccessRequestPersistenceService(AccessRequestRepository accessRequestRepository) {
        this.accessRequestRepository = accessRequestRepository;
    }

    @Transactional
    public AccessRequestResponse saveRequest(AccessRequestEntity entity) {
        // Save trade access request
        AccessRequestEntity savedRequest = accessRequestRepository.save(entity);
        return toResponse(savedRequest);
    }

    @Transactional(readOnly = true)
    public AccessRequestResponse getRequestById(UUID requestId) {
        // Get request from database
        AccessRequestEntity request = accessRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Access request not found"));

        return toResponse(request);
    }

    @Transactional(readOnly = true)
    public Page<AccessRequestResponse> getRequestsByAgent(
            UUID agentId,
            Pageable pageable) {

        // Get trade requests for agent
        return accessRequestRepository.findByAgentId(agentId, pageable)
                .map(this::toResponse);
    }

    public AccessRequestResponse toResponse(AccessRequestEntity request) {
        return new AccessRequestResponse(
                request.getId(),
                request.getAgent().getId(),
                request.getAgent().getAgentCode(),
                request.getSymbol(),
                request.getTradeType(),
                request.getRequestedVolume(),
                request.getRiskScore(),
                request.getOutcome(),
                request.getReasonCode(),
                request.getIdempotencyKey(),
                request.getCreatedAt());
    }
}
