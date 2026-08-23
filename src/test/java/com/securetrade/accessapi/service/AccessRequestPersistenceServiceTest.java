package com.securetrade.accessapi.service;

import com.securetrade.accessapi.common.enums.DecisionResult;
import com.securetrade.accessapi.common.enums.TradeType;
import com.securetrade.accessapi.common.exception.ResourceNotFoundException;
import com.securetrade.accessapi.dto.response.AccessRequestResponse;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.repository.AccessRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessRequestPersistenceServiceTest {

    private static final UUID AGENT_ID =
            UUID.fromString("f9484f91-0408-4ad5-8175-938205a8a4ef");
    private static final UUID REQUEST_ID =
            UUID.fromString("8678257d-a265-43a9-bfb4-7d13ea501249");
    private static final Instant CREATED_AT = Instant.parse("2026-08-23T09:00:00Z");

    @Mock
    private AccessRequestRepository accessRequestRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AccessRequestPersistenceService persistenceService;

    private AccessRequestEntity request;

    @BeforeEach
    void setUp() {
        TradingAgentEntity agent = new TradingAgentEntity();
        agent.setId(AGENT_ID);
        agent.setAgentCode("AGT-004");

        request = new AccessRequestEntity(
                agent,
                "AAPL",
                TradeType.BUY,
                new BigDecimal("1500.00"),
                new BigDecimal("0.30"),
                DecisionResult.APPROVED,
                "TEST_RULE",
                "request-key");
        request.setId(REQUEST_ID);
        request.setCreatedAt(CREATED_AT);
    }

    @Test
    void saveRequestReturnsSavedResponse() {
        when(accessRequestRepository.saveAndFlush(request)).thenReturn(request);

        AccessRequestResponse response = persistenceService.saveRequest(
                request,
                "agent.one");

        assertResponse(response);
        verify(accessRequestRepository).saveAndFlush(request);
        verify(auditLogService).logAction(
                REQUEST_ID,
                "agent.one",
                AuditLogService.TRADE_EVALUATION,
                null,
                DecisionResult.APPROVED.name(),
                "TEST_RULE");
    }

    @Test
    void getRequestByIdReturnsResponse() {
        when(accessRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        AccessRequestResponse response = persistenceService.getRequestById(REQUEST_ID);

        assertResponse(response);
    }

    @Test
    void getRequestByIdStopsWhenRequestIsMissing() {
        when(accessRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> persistenceService.getRequestById(REQUEST_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Access request not found");
    }

    @Test
    void getRequestsByAgentKeepsPageData() {
        PageRequest pageable = PageRequest.of(1, 2);
        Page<AccessRequestEntity> entityPage =
                new PageImpl<>(List.of(request), pageable, 3);
        when(accessRequestRepository.findByAgentId(AGENT_ID, pageable))
                .thenReturn(entityPage);

        Page<AccessRequestResponse> responsePage =
                persistenceService.getRequestsByAgent(AGENT_ID, pageable);

        assertThat(responsePage.getNumber()).isEqualTo(1);
        assertThat(responsePage.getSize()).isEqualTo(2);
        assertThat(responsePage.getTotalElements()).isEqualTo(3);
        assertThat(responsePage.getContent()).hasSize(1);
        assertResponse(responsePage.getContent().get(0));
    }

    private void assertResponse(AccessRequestResponse response) {
        assertThat(response.getId()).isEqualTo(REQUEST_ID);
        assertThat(response.getAgentId()).isEqualTo(AGENT_ID);
        assertThat(response.getAgentCode()).isEqualTo("AGT-004");
        assertThat(response.getSymbol()).isEqualTo("AAPL");
        assertThat(response.getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(response.getRequestedVolume()).isEqualByComparingTo("1500.00");
        assertThat(response.getRiskScore()).isEqualByComparingTo("0.30");
        assertThat(response.getOutcome()).isEqualTo(DecisionResult.APPROVED);
        assertThat(response.getReasonCode()).isEqualTo("TEST_RULE");
        assertThat(response.getIdempotencyKey()).isEqualTo("request-key");
        assertThat(response.getCreatedAt()).isEqualTo(CREATED_AT);
    }
}
