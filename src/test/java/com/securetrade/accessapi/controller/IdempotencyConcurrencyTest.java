package com.securetrade.accessapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.DecisionResult;
import com.securetrade.accessapi.common.enums.TradeType;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.dto.request.LoginRequest;
import com.securetrade.accessapi.dto.request.SubmitTradeAccessRequest;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.entity.UserEntity;
import com.securetrade.accessapi.repository.AccessRequestRepository;
import com.securetrade.accessapi.repository.TradingAgentRepository;
import com.securetrade.accessapi.repository.UserRepository;
import com.securetrade.accessapi.service.AccessRequestPersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyConcurrencyTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private AccessRequestPersistenceService persistenceService;

    private UUID userId;
    private UUID agentId;
    private String username;

    @BeforeEach
    void createCommittedAgent() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        UserEntity user = new UserEntity(
                "race_" + suffix,
                passwordEncoder.encode(PASSWORD),
                UserRole.TRADING_AGENT,
                AgentStatus.ACTIVE);
        user = userRepository.saveAndFlush(user);

        TradingAgentEntity agent = new TradingAgentEntity(
                user,
                "RC-" + suffix.substring(0, 12),
                "Race Agent",
                "MOMENTUM",
                new BigDecimal("20000000.00"));
        agent = tradingAgentRepository.saveAndFlush(agent);

        userId = user.getId();
        agentId = agent.getId();
        username = user.getUsername();
    }

    @AfterEach
    void removeCommittedAgent() {
        if (agentId == null || userId == null) {
            return;
        }

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            List<AccessRequestEntity> requests = accessRequestRepository
                    .findByAgentId(agentId, Pageable.unpaged())
                    .getContent();
            accessRequestRepository.deleteAllInBatch(requests);
            tradingAgentRepository.deleteById(agentId);
            tradingAgentRepository.flush();
            userRepository.deleteById(userId);
            userRepository.flush();
        });
    }

    @Test
    void concurrentDuplicateRequestsReturnOneSavedResult() throws Exception {
        String token = login(username);
        String idempotencyKey = "race-" + UUID.randomUUID();
        String requestBody = objectMapper.writeValueAsString(standardRequest());
        CyclicBarrier insertBarrier = new CyclicBarrier(2);
        AtomicInteger insertAttempts = new AtomicInteger();

        doAnswer(invocation -> {
            insertAttempts.incrementAndGet();
            insertBarrier.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(persistenceService)
                .saveIdempotentRequest(any(AccessRequestEntity.class));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<MvcResult> firstFuture = executor.submit(() ->
                    submitConcurrentRequest(start, token, idempotencyKey, requestBody));
            Future<MvcResult> secondFuture = executor.submit(() ->
                    submitConcurrentRequest(start, token, idempotencyKey, requestBody));

            start.countDown();
            MvcResult firstResult = firstFuture.get(20, TimeUnit.SECONDS);
            MvcResult secondResult = secondFuture.get(20, TimeUnit.SECONDS);

            assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(secondResult.getResponse().getStatus()).isEqualTo(200);

            JsonNode firstResponse = objectMapper.readTree(
                    firstResult.getResponse().getContentAsString());
            JsonNode secondResponse = objectMapper.readTree(
                    secondResult.getResponse().getContentAsString());

            assertThat(secondResponse.get("id").asText())
                    .isEqualTo(firstResponse.get("id").asText());
            assertThat(secondResponse.get("createdAt").asText())
                    .isEqualTo(firstResponse.get("createdAt").asText());
            assertThat(secondResponse.get("outcome").asText())
                    .isEqualTo(DecisionResult.APPROVED.name());
            assertThat(insertAttempts).hasValue(2);

            AccessRequestEntity savedRequest = accessRequestRepository
                    .findByAgentIdAndIdempotencyKey(agentId, idempotencyKey)
                    .orElseThrow();
            assertThat(savedRequest.getId().toString())
                    .isEqualTo(firstResponse.get("id").asText());
            assertThat(accessRequestRepository
                    .findByAgentId(agentId, Pageable.unpaged())
                    .getTotalElements())
                    .isOne();

            verify(persistenceService, times(2))
                    .saveIdempotentRequest(any(AccessRequestEntity.class));
            verify(persistenceService, times(3))
                    .findByAgentIdAndIdempotencyKey(agentId, idempotencyKey);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private MvcResult submitConcurrentRequest(
            CountDownLatch start,
            String token,
            String idempotencyKey,
            String requestBody) throws Exception {

        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test did not start");
        }

        return mockMvc.perform(post("/api/v1/access/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(IDEMPOTENCY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();
    }

    private SubmitTradeAccessRequest standardRequest() {
        return new SubmitTradeAccessRequest(
                "AAPL",
                TradeType.BUY,
                new BigDecimal("500000.00"),
                new BigDecimal("0.20"));
    }

    private String login(String loginUsername) throws Exception {
        LoginRequest request = new LoginRequest(loginUsername, PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("token").asText();
    }
}
