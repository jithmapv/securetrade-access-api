package com.securetrade.accessapi.security.service;

import com.securetrade.accessapi.common.enums.ErrorCode;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.common.exception.SecureTradeAccessDeniedException;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.entity.UserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OwnershipValidationServiceTest {

    private final OwnershipValidationService ownershipValidationService =
            new OwnershipValidationService();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCanAccessAnyRequest() {
        authenticate("admin.one", UserRole.ADMIN);
        AccessRequestEntity request = mock(AccessRequestEntity.class);

        assertThatCode(() -> ownershipValidationService
                .validateRequestOwnership(request, "admin.one"))
                .doesNotThrowAnyException();

        verifyNoInteractions(request);
    }

    @Test
    void agentCanAccessOwnRequest() {
        authenticate("agent.one", UserRole.TRADING_AGENT);
        AccessRequestEntity request = requestOwnedBy("agent.one");

        assertThatCode(() -> ownershipValidationService
                .validateRequestOwnership(request, "agent.one"))
                .doesNotThrowAnyException();
    }

    @Test
    void agentCannotAccessAnotherAgentsRequest() {
        authenticate("agent.two", UserRole.TRADING_AGENT);
        AccessRequestEntity request = requestOwnedBy("agent.one");

        assertThatThrownBy(() -> ownershipValidationService
                .validateRequestOwnership(request, "agent.two"))
                .isInstanceOfSatisfying(
                        SecureTradeAccessDeniedException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(
                                        exception.getErrorCode())
                                .isEqualTo(ErrorCode.ACCESS_DENIED))
                .hasMessage("User is not authorized to access this request");
    }

    private AccessRequestEntity requestOwnedBy(String username) {
        UserEntity user = mock(UserEntity.class);
        TradingAgentEntity agent = mock(TradingAgentEntity.class);
        AccessRequestEntity request = mock(AccessRequestEntity.class);

        when(request.getAgent()).thenReturn(agent);
        when(agent.getUser()).thenReturn(user);
        when(user.getUsername()).thenReturn(username);
        return request;
    }

    private void authenticate(String username, UserRole role) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
