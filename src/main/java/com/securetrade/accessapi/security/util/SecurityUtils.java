package com.securetrade.accessapi.security.util;

import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.common.exception.SecureTradeAccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

public final class SecurityUtils {

    private static final String ROLE_PREFIX = "ROLE_";

    private SecurityUtils() {
    }

    public static String getCurrentUsername() {
        Authentication authentication = getAuthentication();

        if (!isAuthenticated(authentication)
                || !StringUtils.hasText(authentication.getName())) {
            // User is not signed in
            throw new SecureTradeAccessDeniedException("User is not authenticated");
        }

        return authentication.getName();
    }

    public static boolean hasRole(UserRole role) {
        Authentication authentication = getAuthentication();

        if (role == null || !isAuthenticated(authentication)) {
            return false;
        }

        String expectedAuthority = ROLE_PREFIX + role.name();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(expectedAuthority::equals);
    }

    public static boolean isAdmin() {
        return hasRole(UserRole.ADMIN);
    }

    private static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
