package com.securetrade.accessapi.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.DecisionResult;
import com.securetrade.accessapi.common.enums.TradeType;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.decision.ReasonCode;
import com.securetrade.accessapi.dto.request.AdminOverrideRequest;
import com.securetrade.accessapi.dto.request.CreateAgentRequest;
import com.securetrade.accessapi.dto.request.LoginRequest;
import com.securetrade.accessapi.dto.request.SubmitTradeAccessRequest;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.entity.AuditLogEntity;
import com.securetrade.accessapi.entity.UserEntity;
import com.securetrade.accessapi.repository.AccessRequestRepository;
import com.securetrade.accessapi.repository.AuditLogRepository;
import com.securetrade.accessapi.repository.TradingAgentRepository;
import com.securetrade.accessapi.repository.UserRepository;
import com.securetrade.accessapi.service.AuditLogService;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecureTradeE2ETest {

    private static final String PASSWORD = "StrongE2EPassword123!";
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final String APPROVED_KEY = "E2E-KEY-001";
    private static final String MANUAL_REVIEW_KEY = "E2E-KEY-002";

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

    private String adminUsername;
    private String agentUsername;
    private String agentCode;

    @BeforeEach
    void createCommittedAdmin() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);
        adminUsername = "e2e_admin_" + suffix;
        agentUsername = "e2e_agent_" + suffix;
        agentCode = "E2E-" + suffix;

        newTransaction().executeWithoutResult(status -> {
            UserEntity admin = new UserEntity(
                    adminUsername,
                    passwordEncoder.encode(PASSWORD),
                    UserRole.ADMIN,
                    AgentStatus.ACTIVE);
            userRepository.saveAndFlush(admin);
        });
    }

    @AfterEach
    void removeCommittedTestData() {
        newTransaction().executeWithoutResult(status -> {
            // Remove audit logs before linked requests
            List<AuditLogEntity> auditLogs = new ArrayList<>();
            auditLogs.addAll(auditLogRepository
                    .findByActorUsername(adminUsername, Pageable.unpaged())
                    .getContent());
            auditLogs.addAll(auditLogRepository
                    .findByActorUsername(agentUsername, Pageable.unpaged())
                    .getContent());
            auditLogRepository.deleteAllInBatch(auditLogs);
            auditLogRepository.flush();
            entityManager.clear();

            tradingAgentRepository.findByAgentCode(agentCode).ifPresent(agent -> {
                UUID agentId = agent.getId();
                UUID agentUserId = agent.getUser().getId();
                List<AccessRequestEntity> requests = accessRequestRepository
                        .findByAgentId(agentId, Pageable.unpaged())
                        .getContent();
                accessRequestRepository.deleteAllInBatch(requests);
                accessRequestRepository.flush();
                entityManager.clear();

                if (tradingAgentRepository.existsById(agentId)) {
                    tradingAgentRepository.deleteById(agentId);
                    tradingAgentRepository.flush();
                    entityManager.clear();
                }

                if (userRepository.existsById(agentUserId)) {
                    userRepository.deleteById(agentUserId);
                    userRepository.flush();
                }
            });

            userRepository.findByUsername(adminUsername).ifPresent(admin -> {
                userRepository.delete(admin);
                userRepository.flush();
            });
        });
    }

    @Test
    void completeTradingLifecycleCreatesDecisionsAndAuditHistory() throws Exception {
        String adminToken = login(adminUsername);

        JsonNode agentProfile = registerAgent(adminToken);
        UUID agentId = UUID.fromString(agentProfile.get("id").asText());
        String agentToken = login(agentUsername);

        JsonNode approved = evaluate(
                agentToken,
                APPROVED_KEY,
                new SubmitTradeAccessRequest(
                        "AAPL",
                        TradeType.BUY,
                        new BigDecimal("500000.00"),
                        new BigDecimal("0.15")),
                DecisionResult.APPROVED,
                ReasonCode.EXEC_PASS_STANDARD);

        JsonNode approvedReplay = evaluate(
                agentToken,
                APPROVED_KEY,
                new SubmitTradeAccessRequest(
                        "AAPL",
                        TradeType.BUY,
                        new BigDecimal("500000.00"),
                        new BigDecimal("0.15")),
                DecisionResult.APPROVED,
                ReasonCode.EXEC_PASS_STANDARD);

        assertThat(approvedReplay.get("id").asText())
                .isEqualTo(approved.get("id").asText());
        assertThat(approvedReplay.get("createdAt").asText())
                .isEqualTo(approved.get("createdAt").asText());

        JsonNode manualReview = evaluate(
                agentToken,
                MANUAL_REVIEW_KEY,
                new SubmitTradeAccessRequest(
                        "MSFT",
                        TradeType.SELL,
                        new BigDecimal("2500000.00"),
                        new BigDecimal("0.45")),
                DecisionResult.MANUAL_REVIEW,
                ReasonCode.FLAG_HIGH_VOL_RISK);

        UUID approvedRequestId = UUID.fromString(approved.get("id").asText());
        UUID manualReviewRequestId = UUID.fromString(
                manualReview.get("id").asText());
        assertThat(manualReviewRequestId).isNotEqualTo(approvedRequestId);

        assertOwnHistory(
                agentToken,
                approvedRequestId,
                manualReviewRequestId,
                agentId);
        overrideManualReview(adminToken, manualReviewRequestId);
        assertAuditHistory(
                adminToken,
                approvedRequestId,
                manualReviewRequestId);
    }

    private JsonNode registerAgent(String adminToken) throws Exception {
        CreateAgentRequest request = new CreateAgentRequest(
                agentUsername,
                PASSWORD,
                agentCode,
                "E2E Trading Agent",
                "MOMENTUM",
                new BigDecimal("20000000.00"));

        MvcResult result = mockMvc.perform(post("/api/v1/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.username").value(agentUsername))
                .andExpect(jsonPath("$.agentCode").value(agentCode))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("TRADING_AGENT"))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode evaluate(
            String token,
            String idempotencyKey,
            SubmitTradeAccessRequest request,
            DecisionResult expectedOutcome,
            String expectedReasonCode) throws Exception {

        MvcResult result = mockMvc.perform(post("/api/v1/access/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(IDEMPOTENCY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.outcome").value(expectedOutcome.name()))
                .andExpect(jsonPath("$.reasonCode").value(expectedReasonCode))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void assertOwnHistory(
            String agentToken,
            UUID approvedRequestId,
            UUID manualReviewRequestId,
            UUID agentId) throws Exception {

        MvcResult result = mockMvc.perform(get("/api/v1/access/requests/me")
                        .param("page", "0")
                        .param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andReturn();

        JsonNode content = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("content");
        assertThat(textValues(content, "id"))
                .containsExactlyInAnyOrder(
                        approvedRequestId.toString(),
                        manualReviewRequestId.toString());
        assertThat(textValues(content, "agentId"))
                .containsOnly(agentId.toString());
        assertThat(textValues(content, "outcome"))
                .containsExactlyInAnyOrder("APPROVED", "MANUAL_REVIEW");
    }

    private void overrideManualReview(
            String adminToken,
            UUID manualReviewRequestId) throws Exception {

        AdminOverrideRequest request = new AdminOverrideRequest(
                DecisionResult.APPROVED,
                "OVERRIDE_E2E_APPROVED",
                "Approved during E2E test");

        mockMvc.perform(post(
                        "/api/v1/admin/requests/{id}/override",
                        manualReviewRequestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(manualReviewRequestId.toString()))
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.reasonCode")
                        .value("OVERRIDE_E2E_APPROVED"));
    }

    private void assertAuditHistory(
            String adminToken,
            UUID approvedRequestId,
            UUID manualReviewRequestId) throws Exception {

        JsonNode adminLogs = getAuditLogs(adminToken, adminUsername);
        assertThat(textValues(adminLogs, "action"))
                .contains(
                        AuditLogService.AGENT_REGISTRATION,
                        AuditLogService.ADMIN_OVERRIDE);

        JsonNode registrationLog = findAuditByAction(
                adminLogs,
                AuditLogService.AGENT_REGISTRATION);
        assertThat(registrationLog.get("requestId").isNull()).isTrue();
        assertThat(registrationLog.get("newState").asText()).isEqualTo("ACTIVE");

        JsonNode overrideLog = findAuditByAction(
                adminLogs,
                AuditLogService.ADMIN_OVERRIDE);
        assertThat(overrideLog.get("requestId").asText())
                .isEqualTo(manualReviewRequestId.toString());
        assertThat(overrideLog.get("previousState").asText())
                .isEqualTo("MANUAL_REVIEW");
        assertThat(overrideLog.get("newState").asText()).isEqualTo("APPROVED");

        JsonNode agentLogs = getAuditLogs(adminToken, agentUsername);
        assertThat(agentLogs.size()).isEqualTo(2);
        assertThat(textValues(agentLogs, "action"))
                .containsOnly(AuditLogService.TRADE_EVALUATION);
        assertThat(textValues(agentLogs, "requestId"))
                .containsExactlyInAnyOrder(
                        approvedRequestId.toString(),
                        manualReviewRequestId.toString());
        assertThat(textValues(agentLogs, "newState"))
                .containsExactlyInAnyOrder("APPROVED", "MANUAL_REVIEW");
    }

    private JsonNode getAuditLogs(
            String adminToken,
            String actorUsername) throws Exception {

        MvcResult result = mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("page", "0")
                        .param("size", "100")
                        .param("actorUsername", actorUsername)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString());
        return response.get("content");
    }

    private JsonNode findAuditByAction(JsonNode logs, String action) {
        for (JsonNode log : logs) {
            if (action.equals(log.get("action").asText())) {
                return log;
            }
        }

        throw new AssertionError("Audit action not found: " + action);
    }

    private List<String> textValues(JsonNode items, String fieldName) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : items) {
            values.add(item.get(fieldName).asText());
        }
        return values;
    }

    private String login(String username) throws Exception {
        LoginRequest request = new LoginRequest(username, PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString());
        return response.get("token").asText();
    }

    private TransactionTemplate newTransaction() {
        return new TransactionTemplate(transactionManager);
    }
}
