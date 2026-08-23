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
    private final AuditLogService auditLogService;

    public ManualReviewService(
            AccessRequestRepository accessRequestRepository,
            AccessRequestPersistenceService persistenceService,
            AuditLogService auditLogService) {

        this.accessRequestRepository = accessRequestRepository;
        this.persistenceService = persistenceService;
        this.auditLogService = auditLogService;
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

        DecisionResult previousOutcome = request.getOutcome();
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

        // Save admin override audit
        auditLogService.logAction(
                requestId,
                adminUsername,
                AuditLogService.ADMIN_OVERRIDE,
                previousOutcome.name(),
                newOutcome.name(),
                overrideRequest.getAdminNotes());

        return persistenceService.toResponse(savedRequest);
    }
}
