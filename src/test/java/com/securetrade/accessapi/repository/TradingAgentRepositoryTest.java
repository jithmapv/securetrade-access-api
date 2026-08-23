package com.securetrade.accessapi.repository;

import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.entity.UserEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TradingAgentRepositoryTest {

    @Autowired
    private TradingAgentRepository tradingAgentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findByIdLoadsUserForProfileMapping() {
        TradingAgentEntity savedAgent = createAgent();
        entityManager.flush();
        entityManager.clear();

        TradingAgentEntity loadedAgent = tradingAgentRepository
                .findById(savedAgent.getId())
                .orElseThrow();

        assertUserIsLoaded(loadedAgent);
    }

    @Test
    void findByUsernameLoadsUserForDecisionChecks() {
        TradingAgentEntity savedAgent = createAgent();
        String username = savedAgent.getUser().getUsername();
        entityManager.flush();
        entityManager.clear();

        TradingAgentEntity loadedAgent = tradingAgentRepository
                .findByUserUsername(username)
                .orElseThrow();

        assertUserIsLoaded(loadedAgent);
    }

    @Test
    void lockedAgentLookupLoadsUserForStatusUpdate() {
        TradingAgentEntity savedAgent = createAgent();
        entityManager.flush();
        entityManager.clear();

        TradingAgentEntity loadedAgent = tradingAgentRepository
                .findByIdForUpdate(savedAgent.getId())
                .orElseThrow();

        assertUserIsLoaded(loadedAgent);
    }

    private void assertUserIsLoaded(TradingAgentEntity agent) {
        PersistenceUnitUtil persistenceUnitUtil = entityManager
                .getEntityManagerFactory()
                .getPersistenceUnitUtil();

        assertThat(persistenceUnitUtil.isLoaded(agent, "user")).isTrue();
        assertThat(agent.getUser().getUsername()).startsWith("fetch_");
    }

    private TradingAgentEntity createAgent() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        UserEntity user = new UserEntity(
                "fetch_" + suffix,
                "test-password-hash",
                UserRole.TRADING_AGENT,
                AgentStatus.ACTIVE);
        user = userRepository.saveAndFlush(user);

        TradingAgentEntity agent = new TradingAgentEntity(
                user,
                "FETCH-" + suffix.substring(0, 12),
                "Fetch Plan Agent",
                "MOMENTUM",
                new BigDecimal("100000.00"));
        return tradingAgentRepository.saveAndFlush(agent);
    }
}
