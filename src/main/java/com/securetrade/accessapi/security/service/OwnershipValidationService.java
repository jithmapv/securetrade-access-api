package com.securetrade.accessapi.security.service;

import com.securetrade.accessapi.common.exception.SecureTradeAccessDeniedException;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.security.util.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class OwnershipValidationService {

    public void validateRequestOwnership(
            AccessRequestEntity request,
            String currentUsername) {

        // Allow admins to access any request
        if (SecurityUtils.isAdmin()) {
            return;
        }

        String ownerUsername = request.getAgent().getUser().getUsername();
        if (Objects.equals(ownerUsername, currentUsername)) {
            return;
        }

        // Block access to another agent request
        throw new SecureTradeAccessDeniedException(
                "User is not authorized to access this request");
    }
}
