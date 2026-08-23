package com.securetrade.accessapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.DecisionResult;
import com.securetrade.accessapi.common.enums.TradeType;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.decision.ReasonCode;
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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccessEvaluationControllerTest {

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
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<CommittedAgent> committedAgents = new ArrayList<>();

    @AfterEach
    void removeCommittedAgents() {
        for (CommittedAgent committedAgent : committedAgents) {
            newTransaction().executeWithoutResult(status -> {
                List<AuditLogEntity> auditLogs = auditLogRepository
                        .findByActorUsername(
                                committedAgent.username(),
                                Pageable.unpaged())
                        .getContent();
                auditLogRepository.deleteAllInBatch(auditLogs);
                auditLogRepository.flush();

                List<AccessRequestEntity> requests = accessRequestRepository
                        .findByAgentId(committedAgent.agentId(), Pageable.unpaged())
                        .getContent();
                accessRequestRepository.deleteAllInBatch(requests);
                accessRequestRepository.flush();
                entityManager.clear();
                tradingAgentRepository.deleteById(committedAgent.agentId());
                tradingAgentRepository.flush();
                userRepository.deleteById(committedAgent.userId());
                userRepository.flush();
            });
        }
    }

    @Test
    void authenticatedAgentCanEvaluateTrade() throws Exception {
        createAgent(UserRole.TRADING_AGENT);
        TradingAgentEntity agent = createAgent(UserRole.TRADING_AGENT);
        String token = login(agent.getUser().getUsername());
        SubmitTradeAccessRequest request = standardRequest();

        MvcResult result = mockMvc.perform(post("/api/v1/access/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.agentId").value(agent.getId().toString()))
                .andExpect(jsonPath("$.agentCode").value(agent.getAgentCode()))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.tradeType").value("BUY"))
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.reasonCode").value(ReasonCode.EXEC_PASS_STANDARD))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID requestId = UUID.fromString(response.get("id").asText());
        AccessRequestEntity savedRequest = accessRequestRepository.findById(requestId)
                .orElseThrow();

        assertThat(savedRequest.getAgent().getId()).isEqualTo(agent.getId());
        assertThat(savedRequest.getOutcome()).isEqualTo(DecisionResult.APPROVED);
        assertThat(savedRequest.getReasonCode()).isEqualTo(ReasonCode.EXEC_PASS_STANDARD);
        assertThat(savedRequest.getIdempotencyKey()).isNull();
    }

    @Test
    void invalidTradeRequestReturnsBadRequest() throws Exception {
        TradingAgentEntity agent = createAgent(UserRole.TRADING_AGENT);
        String token = login(agent.getUser().getUsername());
        SubmitTradeAccessRequest request = new SubmitTradeAccessRequest(
                "bad symbol",
                TradeType.SELL,
                BigDecimal.ZERO,
                new BigDecimal("1.10"));

        mockMvc.perform(post("/api/v1/access/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(accessRequestRepository
                .findByAgentId(agent.getId(), PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    void requestWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/access/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(standardRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminWithoutAgentProfileReturnsNotFound() throws Exception {
        UserEntity admin = createUser(UserRole.ADMIN);
        String token = login(admin.getUsername());

        mockMvc.perform(post("/api/v1/access/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(standardRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void agentLookupLoadsUserStatus() {
        TradingAgentEntity agent = createAgent(UserRole.TRADING_AGENT);
        String username = agent.getUser().getUsername();
        entityManager.flush();
        entityManager.clear();

        TradingAgentEntity loadedAgent = tradingAgentRepository
                .findByUserUsername(username)
                .orElseThrow();

        assertThat(entityManager.getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .isLoaded(loadedAgent, "user"))
                .isTrue();
    }

    @Test
    void sameIdempotencyKeyReturnsSameResponse() throws Exception {
        CommittedAgent agent = createCommittedAgent();
        String token = login(agent.username());
        String key = "idem-" + UUID.randomUUID();
        String requestBody = objectMapper.writeValueAsString(standardRequest());

        MvcResult firstResult = mockMvc.perform(post("/api/v1/access/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(IDEMPOTENCY_HEADER, "  " + key + "  ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult secondResult = mockMvc.perform(post("/api/v1/access/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(IDEMPOTENCY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstResponse = objectMapper.readTree(
                firstResult.getResponse().getContentAsString());
        JsonNode secondResponse = objectMapper.readTree(
                secondResult.getResponse().getContentAsString());

        assertThat(secondResponse.get("id").asText())
                .isEqualTo(firstResponse.get("id").asText());
        assertThat(secondResponse.get("createdAt").asText())
                .isEqualTo(firstResponse.get("createdAt").asText());

        AccessRequestEntity savedRequest = accessRequestRepository
                .findByAgentIdAndIdempotencyKey(agent.agentId(), key)
                .orElseThrow();
        assertThat(savedRequest.getId().toString())
                .isEqualTo(firstResponse.get("id").asText());
        assertThat(accessRequestRepository
                .findByAgentId(agent.agentId(), Pageable.unpaged())
                .getTotalElements())
                .isOne();

        UUID requestId = UUID.fromString(firstResponse.get("id").asText());
        List<AuditLogEntity> auditLogs = auditLogRepository
                .findByRequestId(requestId, Pageable.unpaged())
                .getContent();
        assertThat(auditLogs).singleElement().satisfies(audit -> {
            assertThat(audit.getActorUsername()).isEqualTo(agent.username());
            assertThat(audit.getAction())
                    .isEqualTo(AuditLogService.TRADE_EVALUATION);
            assertThat(audit.getPreviousState()).isNull();
            assertThat(audit.getNewState()).isEqualTo("APPROVED");
            assertThat(audit.getDetails())
                    .isEqualTo(ReasonCode.EXEC_PASS_STANDARD);
        });
    }

    @Test
    void requestsWithoutIdempotencyKeyCreateDistinctRecords() throws Exception {
        TradingAgentEntity agent = createAgent(UserRole.TRADING_AGENT);
        String token = login(agent.getUser().getUsername());
        String requestBody = objectMapper.writeValueAsString(standardRequest());

        MvcResult firstResult = mockMvc.perform(post("/api/v1/access/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult secondResult = mockMvc.perform(post("/api/v1/access/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstResponse = objectMapper.readTree(
                firstResult.getResponse().getContentAsString());
        JsonNode secondResponse = objectMapper.readTree(
                secondResult.getResponse().getContentAsString());

        assertThat(secondResponse.get("id").asText())
                .isNotEqualTo(firstResponse.get("id").asText());
        assertThat(accessRequestRepository
                .findByAgentId(agent.getId(), Pageable.unpaged())
                .getTotalElements())
                .isEqualTo(2);
    }

    @Test
    void blankIdempotencyKeyActsLikeMissingKey() throws Exception {
        TradingAgentEntity agent = createAgent(UserRole.TRADING_AGENT);
        String token = login(agent.getUser().getUsername());

        MvcResult result = mockMvc.perform(post("/api/v1/access/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(IDEMPOTENCY_HEADER, "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(standardRequest())))
                .andExpect(status().isOk())
                .andReturn();

        UUID requestId = UUID.fromString(objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText());
        AccessRequestEntity savedRequest = accessRequestRepository.findById(requestId)
                .orElseThrow();
        assertThat(savedRequest.getIdempotencyKey()).isNull();
    }

    @Test
    void longIdempotencyKeyReturnsBadRequest() throws Exception {
        TradingAgentEntity agent = createAgent(UserRole.TRADING_AGENT);
        String token = login(agent.getUser().getUsername());

        mockMvc.perform(post("/api/v1/access/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(IDEMPOTENCY_HEADER, "K".repeat(65))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(standardRequest())))
                .andExpect(status().isBadRequest());

        assertThat(accessRequestRepository
                .findByAgentId(agent.getId(), Pageable.unpaged()))
                .isEmpty();
    }

    private TradingAgentEntity createAgent(UserRole role) {
        UserEntity user = createUser(role);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        TradingAgentEntity agent = new TradingAgentEntity(
                user,
                "EV-" + suffix.substring(0, 12),
                "Evaluation Agent",
                "MOMENTUM",
                new BigDecimal("20000000.00"));
        return tradingAgentRepository.saveAndFlush(agent);
    }

    private UserEntity createUser(UserRole role) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        UserEntity user = new UserEntity(
                "eval_" + suffix,
                passwordEncoder.encode(PASSWORD),
                role,
                AgentStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private CommittedAgent createCommittedAgent() {
        CommittedAgent committedAgent = newTransaction().execute(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            UserEntity user = new UserEntity(
                    "idem_" + suffix,
                    passwordEncoder.encode(PASSWORD),
                    UserRole.TRADING_AGENT,
                    AgentStatus.ACTIVE);
            user = userRepository.saveAndFlush(user);

            TradingAgentEntity agent = new TradingAgentEntity(
                    user,
                    "ID-" + suffix.substring(0, 12),
                    "Idempotency Agent",
                    "MOMENTUM",
                    new BigDecimal("20000000.00"));
            agent = tradingAgentRepository.saveAndFlush(agent);
            return new CommittedAgent(
                    user.getId(),
                    agent.getId(),
                    user.getUsername());
        });

        if (committedAgent == null) {
            throw new IllegalStateException("Could not create test agent");
        }

        committedAgents.add(committedAgent);
        return committedAgent;
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }

    private SubmitTradeAccessRequest standardRequest() {
        return new SubmitTradeAccessRequest(
                "AAPL",
                TradeType.BUY,
                new BigDecimal("500000.00"),
                new BigDecimal("0.20"));
    }

    private String login(String username) throws Exception {
        LoginRequest request = new LoginRequest(username, PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("token").asText();
    }

    private record CommittedAgent(
            UUID userId,
            UUID agentId,
            String username) {
    }
}
