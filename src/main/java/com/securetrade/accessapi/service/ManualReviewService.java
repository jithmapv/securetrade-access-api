package com.securetrade.accessapi.service;

import com.securetrade.accessapi.common.enums.DecisionResult;
import com.securetrade.accessapi.common.exception.InvalidRequestException;
import com.securetrade.accessapi.common.exception.ResourceNotFoundException;
import com.securetrade.accessapi.dto.request.AdminOverrideRequest;
import com.securetrade.accessapi.dto.response.AccessRequestResponse;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.repository.AccessRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ManualReviewService {

    private final AccessRequestRepository accessRequestRepository;
    private final AccessRequestPersistenceService persistenceService;

    public ManualReviewService(
            AccessRequestRepository accessRequestRepository,
            AccessRequestPersistenceService persistenceService) {

        this.accessRequestRepository = accessRequestRepository;
        this.persistenceService = persistenceService;
    }

    @Transactional
    public AccessRequestResponse processOverride(
            UUID requestId,
            AdminOverrideRequest overrideRequest,
            String adminUsername) {

        // Lock request while admin updates it
        AccessRequestEntity request = accessRequestRepository
                .findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Access request not found"));

        // Check if request is waiting for manual review
        if (request.getOutcome() != DecisionResult.MANUAL_REVIEW) {
            throw new InvalidRequestException(
                    "Only requests in MANUAL_REVIEW status can be overridden");
        }

        DecisionResult newOutcome = overrideRequest.getOutcome();
        if (newOutcome != DecisionResult.APPROVED
                && newOutcome != DecisionResult.REJECTED) {
            throw new InvalidRequestException(
                    "Override outcome must be APPROVED or REJECTED");
        }

        // Save admin override decision
        request.setOutcome(newOutcome);
        request.setReasonCode(overrideRequest.getReasonCode());
        AccessRequestEntity savedRequest = accessRequestRepository.save(request);

        return persistenceService.toResponse(savedRequest);
    }
}
