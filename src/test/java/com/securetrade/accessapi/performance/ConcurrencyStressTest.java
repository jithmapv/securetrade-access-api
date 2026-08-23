package com.securetrade.accessapi.performance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.DecisionResult;
import com.securetrade.accessapi.common.enums.TradeType;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.decision.ReasonCode;
import com.securetrade.accessapi.dto.request.AdminOverrideRequest;
import com.securetrade.accessapi.dto.request.LoginRequest;
import com.securetrade.accessapi.dto.request.SubmitTradeAccessRequest;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.entity.AuditLogEntity;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.entity.UserEntity;
import com.securetrade.accessapi.repository.AccessRequestRepository;
import com.securetrade.accessapi.repository.AuditLogRepository;
import com.securetrade.accessapi.repository.TradingAgentRepository;
import com.securetrade.accessapi.repository.UserRepository;
import com.securetrade.accessapi.service.AuditLogService;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.minimum-idle=5",
        "spring.datasource.hikari.connection-timeout=10000"
})
@AutoConfigureMockMvc
class ConcurrencyStressTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrencyStressTest.class);
    private static final int CONCURRENT_REQUESTS = 20;
    private static final Duration LOAD_TIMEOUT = Duration.ofSeconds(45);
    private static final String PASSWORD = "StrongTestPassword123!";
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TradingAgentRepository tradingAgentRepository;

    @Autowired
    private AccessRequestRepository accessRequestRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private UUID adminUserId;
    private UUID agentUserId;
    private UUID agentId;
    private String adminUsername;
    private String agentUsername;

    @BeforeEach
    void createCommittedUsers() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");

            UserEntity admin = new UserEntity(
                    "stress_admin_" + suffix,
                    passwordEncoder.encode(PASSWORD),
                    UserRole.ADMIN,
                    AgentStatus.ACTIVE);
            admin = userRepository.saveAndFlush(admin);

            UserEntity agentUser = new UserEntity(
                    "stress_agent_" + suffix,
                    passwordEncoder.encode(PASSWORD),
                    UserRole.TRADING_AGENT,
                    AgentStatus.ACTIVE);
            agentUser = userRepository.saveAndFlush(agentUser);

            TradingAgentEntity agent = new TradingAgentEntity(
                    agentUser,
                    "ST-" + suffix.substring(0, 12),
                    "Stress Test Agent",
                    "MOMENTUM",
                    new BigDecimal("20000000.00"));
            agent = tradingAgentRepository.saveAndFlush(agent);

            adminUserId = admin.getId();
            agentUserId = agentUser.getId();
            agentId = agent.getId();
            adminUsername = admin.getUsername();
            agentUsername = agentUser.getUsername();
        });
    }

    @AfterEach
    void removeCommittedUsers() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // Remove audit rows first
        transactionTemplate.executeWithoutResult(status -> {
            removeAuditLogs(adminUsername);
            removeAuditLogs(agentUsername);
            auditLogRepository.flush();
        });

        transactionTemplate.executeWithoutResult(status -> {
            if (agentId != null) {
                List<AccessRequestEntity> requests = accessRequestRepository
                        .findByAgentId(agentId, Pageable.unpaged())
                        .getContent();
                accessRequestRepository.deleteAllInBatch(requests);
                accessRequestRepository.flush();
            }
        });

        transactionTemplate.executeWithoutResult(status -> {
            if (agentId != null && tradingAgentRepository.existsById(agentId)) {
                tradingAgentRepository.deleteById(agentId);
                tradingAgentRepository.flush();
            }
        });

        transactionTemplate.executeWithoutResult(status -> {
            removeUser(agentUserId);
            removeUser(adminUserId);
            userRepository.flush();
        });
    }

    @Test
    void twentyConcurrentEvaluationsConvergeToOneResult() throws Exception {

        String agentToken = login(agentUsername);
        String idempotencyKey = "stress-" + UUID.randomUUID();
        String evaluationBody = objectMapper.writeValueAsString(manualReviewRequest());

        HikariDataSource hikariDataSource = dataSource.unwrap(HikariDataSource.class);
        HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
        AtomicInteger peakActiveConnections = new AtomicInteger();
        ScheduledExecutorService poolMonitor = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> poolSample = poolMonitor.scheduleAtFixedRate(
                () -> peakActiveConnections.accumulateAndGet(
                        pool.getActiveConnections(),
                        Math::max),
                0,
                1,
                TimeUnit.MILLISECONDS);

        LoadResult<MvcResult> evaluationLoad;
        try {
            evaluationLoad = runConcurrently(() ->
                    mockMvc.perform(post("/api/v1/access/evaluate")
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken)
                                    .header(IDEMPOTENCY_HEADER, idempotencyKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(evaluationBody))
                            .andReturn());
        } finally {
            poolSample.cancel(false);
            poolMonitor.shutdownNow();
            assertThat(poolMonitor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        List<MvcResult> evaluationResults = evaluationLoad.results();
        assertThat(evaluationResults).hasSize(CONCURRENT_REQUESTS);
        assertThat(evaluationResults)
                .allSatisfy(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        Set<String> responseIds = new HashSet<>();
        Set<String> responseTimes = new HashSet<>();
        for (MvcResult result : evaluationResults) {
            JsonNode response = objectMapper.readTree(
                    result.getResponse().getContentAsString());
            responseIds.add(response.get("id").asText());
            responseTimes.add(response.get("createdAt").asText());
            assertThat(response.get("outcome").asText())
                    .isEqualTo(DecisionResult.MANUAL_REVIEW.name());
        }

        // All calls must return one saved result
        assertThat(responseIds).singleElement();
        assertThat(responseTimes).singleElement();
        UUID requestId = UUID.fromString(responseIds.iterator().next());

        AccessRequestEntity savedRequest = accessRequestRepository
                .findByAgentIdAndIdempotencyKey(agentId, idempotencyKey)
                .orElseThrow();
        assertThat(savedRequest.getId()).isEqualTo(requestId);
        assertThat(accessRequestRepository
                .findByAgentId(agentId, Pageable.unpaged())
                .getTotalElements())
                .isOne();

        List<AuditLogEntity> evaluationAudits = auditLogRepository
                .findByRequestId(requestId, Pageable.unpaged())
                .getContent();
        assertThat(evaluationAudits).singleElement().satisfies(audit -> {
            assertThat(audit.getActorUsername()).isEqualTo(agentUsername);
            assertThat(audit.getAction()).isEqualTo(AuditLogService.TRADE_EVALUATION);
            assertThat(audit.getNewState()).isEqualTo(DecisionResult.MANUAL_REVIEW.name());
            assertThat(audit.getDetails()).isEqualTo(ReasonCode.FLAG_HIGH_VOL_RISK);
        });

        int maximumPoolSize = hikariDataSource.getMaximumPoolSize();
        assertThat(maximumPoolSize).isEqualTo(5);
        assertThat(CONCURRENT_REQUESTS).isGreaterThan(maximumPoolSize);
        assertThat(peakActiveConnections.get()).isBetween(1, maximumPoolSize);
        assertThat(pool.getTotalConnections()).isLessThanOrEqualTo(maximumPoolSize);
        assertThat(pool.getThreadsAwaitingConnection()).isZero();

        assertThat(evaluationLoad.elapsed()).isLessThan(LOAD_TIMEOUT);
        LOGGER.info(
                "Twenty trade evaluations finished in {} ms. Hikari peak was {}/{}.",
                evaluationLoad.elapsed().toMillis(),
                peakActiveConnections.get(),
                maximumPoolSize);
    }

    @Test
    void concurrentOverridesUseOnePessimisticRowLock() throws Exception {
        UUID requestId = createCommittedManualReviewRequest();
        String adminToken = login(adminUsername);

        String overrideBody = objectMapper.writeValueAsString(
                new AdminOverrideRequest(
                        DecisionResult.APPROVED,
                        "OVERRIDE_LOAD_APPROVED",
                        "Concurrent lock test"));

        LoadResult<MvcResult> overrideLoad = runConcurrently(() ->
                mockMvc.perform(post(
                                "/api/v1/admin/requests/{id}/override",
                                requestId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(overrideBody))
                        .andReturn());

        List<MvcResult> overrideResults = overrideLoad.results();
        long successfulOverrides = overrideResults.stream()
                .filter(result -> result.getResponse().getStatus() == 200)
                .count();
        long rejectedOverrides = overrideResults.stream()
                .filter(result -> result.getResponse().getStatus() == 400)
                .count();

        // The database lock allows one final decision
        assertThat(successfulOverrides).isOne();
        assertThat(rejectedOverrides).isEqualTo(CONCURRENT_REQUESTS - 1L);
        assertThat(overrideResults).hasSize(CONCURRENT_REQUESTS);
        assertThat(accessRequestRepository.findById(requestId)
                .orElseThrow()
                .getOutcome())
                .isEqualTo(DecisionResult.APPROVED);

        List<AuditLogEntity> overrideAudits = auditLogRepository
                .findByRequestId(requestId, Pageable.unpaged())
                .getContent();
        assertThat(overrideAudits).singleElement().satisfies(audit -> {
            assertThat(audit.getAction()).isEqualTo(AuditLogService.ADMIN_OVERRIDE);
            assertThat(audit.getActorUsername()).isEqualTo(adminUsername);
            assertThat(audit.getPreviousState())
                    .isEqualTo(DecisionResult.MANUAL_REVIEW.name());
            assertThat(audit.getNewState())
                    .isEqualTo(DecisionResult.APPROVED.name());
        });

        assertThat(overrideLoad.elapsed()).isLessThan(LOAD_TIMEOUT);
        LOGGER.info(
                "Twenty locked overrides finished in {} ms.",
                overrideLoad.elapsed().toMillis());
    }

    private <T> LoadResult<T> runConcurrently(ConcurrentCall<T> call) throws Exception {
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        List<Future<T>> futures = new ArrayList<>(CONCURRENT_REQUESTS);

        try {
            for (int index = 0; index < CONCURRENT_REQUESTS; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent load did not start");
                    }
                    return call.execute();
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + LOAD_TIMEOUT.toNanos();
            long startedAt = System.nanoTime();
            start.countDown();

            List<T> results = new ArrayList<>(CONCURRENT_REQUESTS);
            for (Future<T> future : futures) {
                long remaining = deadline - System.nanoTime();
                assertThat(remaining).isPositive();
                results.add(future.get(remaining, TimeUnit.NANOSECONDS));
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
            return new LoadResult<>(results, elapsed);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private UUID createCommittedManualReviewRequest() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID requestId = transactionTemplate.execute(status -> {
            TradingAgentEntity agent = tradingAgentRepository.findById(agentId)
                    .orElseThrow();
            AccessRequestEntity request = new AccessRequestEntity(
                    agent,
                    "AAPL",
                    TradeType.BUY,
                    new BigDecimal("1500000.00"),
                    new BigDecimal("0.50"),
                    DecisionResult.MANUAL_REVIEW,
                    ReasonCode.FLAG_HIGH_VOL_RISK,
                    "lock-" + UUID.randomUUID());
            return accessRequestRepository.saveAndFlush(request).getId();
        });
        return Objects.requireNonNull(requestId);
    }

    private SubmitTradeAccessRequest manualReviewRequest() {
        return new SubmitTradeAccessRequest(
                "AAPL",
                TradeType.BUY,
                new BigDecimal("1500000.00"),
                new BigDecimal("0.50"));
    }

    private String login(String username) throws Exception {
        LoginRequest request = new LoginRequest(username, PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString());
        return response.get("token").asText();
    }

    private void removeAuditLogs(String username) {
        if (username == null) {
            return;
        }

        List<AuditLogEntity> auditLogs = auditLogRepository
                .findByActorUsername(username, Pageable.unpaged())
                .getContent();
        auditLogRepository.deleteAllInBatch(auditLogs);
    }

    private void removeUser(UUID userId) {
        if (userId != null && userRepository.existsById(userId)) {
            userRepository.deleteById(userId);
        }
    }

    @FunctionalInterface
    private interface ConcurrentCall<T> {

        T execute() throws Exception;
    }

    private record LoadResult<T>(List<T> results, Duration elapsed) {
    }
}
