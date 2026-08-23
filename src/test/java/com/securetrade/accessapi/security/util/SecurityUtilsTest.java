package com.securetrade.accessapi.security.util;

import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.common.exception.SecureTradeAccessDeniedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUsernameReturnsAuthenticatedUsername() {
        authenticate("agent.one", UserRole.TRADING_AGENT);

        assertThat(SecurityUtils.getCurrentUsername()).isEqualTo("agent.one");
    }

    @Test
    void getCurrentUsernameRejectsMissingAuthentication() {
        assertThatThrownBy(SecurityUtils::getCurrentUsername)
                .isInstanceOf(SecureTradeAccessDeniedException.class)
                .hasMessage("User is not authenticated");
    }

    @Test
    void getCurrentUsernameRejectsAnonymousAuthentication() {
        AnonymousAuthenticationToken authentication = new AnonymousAuthenticationToken(
                "test-key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        setAuthentication(authentication);

        assertThatThrownBy(SecurityUtils::getCurrentUsername)
                .isInstanceOf(SecureTradeAccessDeniedException.class)
                .hasMessage("User is not authenticated");
    }

    @Test
    void roleHelpersUseCurrentAuthorities() {
        authenticate("admin.one", UserRole.ADMIN);

        assertThat(SecurityUtils.hasRole(UserRole.ADMIN)).isTrue();
        assertThat(SecurityUtils.hasRole(UserRole.TRADING_AGENT)).isFalse();
        assertThat(SecurityUtils.isAdmin()).isTrue();
    }

    @Test
    void roleHelpersRejectUnauthenticatedUser() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("agent.one", null);
        setAuthentication(authentication);

        assertThat(SecurityUtils.hasRole(UserRole.TRADING_AGENT)).isFalse();
        assertThat(SecurityUtils.isAdmin()).isFalse();
    }

    private void authenticate(String username, UserRole role) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        setAuthentication(authentication);
    }

    private void setAuthentication(
            org.springframework.security.core.Authentication authentication) {

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
