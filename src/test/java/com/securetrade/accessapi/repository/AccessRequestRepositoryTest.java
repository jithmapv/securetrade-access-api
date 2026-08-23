package com.securetrade.accessapi.repository;

import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.DecisionResult;
import com.securetrade.accessapi.common.enums.TradeType;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccessRequestRepositoryTest {

    @Autowired
    private AccessRequestRepository accessRequestRepository;

    @Autowired
    private TradingAgentRepository tradingAgentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsRequestsByAgentWithPagination() {
        TradingAgentEntity agent = createAgent();
        TradingAgentEntity otherAgent = createAgent();

        AccessRequestEntity oldest = saveRequest(
                agent,
                "AAPL",
                "key-a",
                Instant.parse("2026-08-23T07:00:00Z"));
        saveRequest(
                agent,
                "MSFT",
                "key-b",
                Instant.parse("2026-08-23T07:01:00Z"));
        saveRequest(
                agent,
                "TSLA",
                "key-c",
                Instant.parse("2026-08-23T07:02:00Z"));
        saveRequest(
                otherAgent,
                "NVDA",
                "key-d",
                Instant.parse("2026-08-23T07:03:00Z"));

        PageRequest firstPageRequest = PageRequest.of(
                0,
                2,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AccessRequestEntity> firstPage =
                accessRequestRepository.findByAgentId(agent.getId(), firstPageRequest);

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent())
                .extracting(AccessRequestEntity::getSymbol)
                .containsExactly("TSLA", "MSFT");

        PageRequest secondPageRequest = PageRequest.of(
                1,
                2,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AccessRequestEntity> secondPage =
                accessRequestRepository.findByAgentId(agent.getId(), secondPageRequest);

        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.getContent().get(0).getId()).isEqualTo(oldest.getId());
        assertThat(secondPage.getContent().get(0).getRequestedVolume())
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void findsIdempotencyKeyInsideAgent() {
        TradingAgentEntity firstAgent = createAgent();
        TradingAgentEntity secondAgent = createAgent();
        AccessRequestEntity firstRequest = saveRequest(
                firstAgent,
                "AAPL",
                "shared-key",
                Instant.parse("2026-08-23T08:00:00Z"));
        AccessRequestEntity secondRequest = saveRequest(
                secondAgent,
                "MSFT",
                "shared-key",
                Instant.parse("2026-08-23T08:01:00Z"));

        AccessRequestEntity foundFirst = accessRequestRepository
                .findByAgentIdAndIdempotencyKey(firstAgent.getId(), "shared-key")
                .orElseThrow();
        AccessRequestEntity foundSecond = accessRequestRepository
                .findByAgentIdAndIdempotencyKey(secondAgent.getId(), "shared-key")
                .orElseThrow();

        assertThat(foundFirst.getId()).isEqualTo(firstRequest.getId());
        assertThat(foundSecond.getId()).isEqualTo(secondRequest.getId());
    }

    private TradingAgentEntity createAgent() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        UserEntity user = new UserEntity(
                "p4_" + suffix,
                "test-password-hash",
                UserRole.TRADING_AGENT,
                AgentStatus.ACTIVE);
        UserEntity savedUser = userRepository.saveAndFlush(user);

        TradingAgentEntity agent = new TradingAgentEntity(
                savedUser,
                "P4-" + suffix.substring(0, 12),
                "Phase Four Agent",
                "MOMENTUM",
                new BigDecimal("100000.00"));
        return tradingAgentRepository.saveAndFlush(agent);
    }

    private AccessRequestEntity saveRequest(
            TradingAgentEntity agent,
            String symbol,
            String idempotencyKey,
            Instant createdAt) {

        AccessRequestEntity request = new AccessRequestEntity(
                agent,
                symbol,
                TradeType.BUY,
                new BigDecimal("1000.00"),
                new BigDecimal("0.25"),
                DecisionResult.APPROVED,
                "TEST_RULE",
                idempotencyKey);
        request.setCreatedAt(createdAt);
        return accessRequestRepository.saveAndFlush(request);
    }
}
