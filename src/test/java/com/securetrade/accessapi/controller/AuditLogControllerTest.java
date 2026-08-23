package com.securetrade.accessapi.controller;

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
import com.securetrade.accessapi.dto.request.UpdateAgentStatusRequest;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.entity.AuditLogEntity;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.entity.UserEntity;
import com.securetrade.accessapi.repository.AccessRequestRepository;
import com.securetrade.accessapi.repository.AuditLogRepository;
import com.securetrade.accessapi.repository.TradingAgentRepository;
import com.securetrade.accessapi.repository.UserRepository;
import com.securetrade.accessapi.service.AuditLogService;
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
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditLogControllerTest {

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

    private final Set<String> auditActors = new HashSet<>();

    private UUID adminUserId;
    private UUID agentUserId;
    private UUID agentId;
    private String adminUsername;
    private String agentUsername;

    @BeforeEach
    void createCommittedUsers() {
        newTransaction().executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");

            UserEntity admin = new UserEntity(
                    "audit_admin_" + suffix,
                    passwordEncoder.encode(PASSWORD),
                    UserRole.ADMIN,
                    AgentStatus.ACTIVE);
            admin = userRepository.saveAndFlush(admin);

            UserEntity agentUser = new UserEntity(
                    "audit_agent_" + suffix,
                    passwordEncoder.encode(PASSWORD),
                    UserRole.TRADING_AGENT,
                    AgentStatus.ACTIVE);
            agentUser = userRepository.saveAndFlush(agentUser);

            TradingAgentEntity agent = new TradingAgentEntity(
                    agentUser,
                    "AU-" + suffix.substring(0, 12),
                    "Audit Agent",
                    "MOMENTUM",
                    new BigDecimal("20000000.00"));
            agent = tradingAgentRepository.saveAndFlush(agent);

            adminUserId = admin.getId();
            agentUserId = agentUser.getId();
            agentId = agent.getId();
            adminUsername = admin.getUsername();
            agentUsername = agentUser.getUsername();
        });

        auditActors.add(adminUsername);
        auditActors.add(agentUsername);
    }

    @AfterEach
    void removeCommittedData() {
        newTransaction().executeWithoutResult(status -> {
            for (String actorUsername : auditActors) {
                List<AuditLogEntity> auditLogs = auditLogRepository
                        .findByActorUsername(actorUsername, Pageable.unpaged())
                        .getContent();
                auditLogRepository.deleteAllInBatch(auditLogs);
            }
            auditLogRepository.flush();

            if (agentId != null) {
                List<AccessRequestEntity> requests = accessRequestRepository
                        .findByAgentId(agentId, Pageable.unpaged())
                        .getContent();
                accessRequestRepository.deleteAllInBatch(requests);
                accessRequestRepository.flush();
            }

            if (agentId != null && tradingAgentRepository.existsById(agentId)) {
                tradingAgentRepository.deleteById(agentId);
                tradingAgentRepository.flush();
            }

            if (agentUserId != null && userRepository.existsById(agentUserId)) {
                userRepository.deleteById(agentUserId);
            }

            if (adminUserId != null && userRepository.existsById(adminUserId)) {
                userRepository.deleteById(adminUserId);
            }
            userRepository.flush();
        });
    }

    @Test
    void adminCanGetFilteredAuditLogsWithPagination() throws Exception {
        String filterActor = "filter_" + UUID.randomUUID()
                .toString()
                .replace("-", "");
        String otherActor = "other_" + UUID.randomUUID()
                .toString()
                .replace("-", "");
        auditActors.add(filterActor);
        auditActors.add(otherActor);

        AuditLogEntity oldest = saveAudit(
                filterActor,
                "ONE",
                Instant.parse("2026-08-20T08:00:00Z"));
        AuditLogEntity middle = saveAudit(
                filterActor,
                "TWO",
                Instant.parse("2026-08-21T08:00:00Z"));
        AuditLogEntity newest = saveAudit(
                filterActor,
                "THREE",
                Instant.parse("2026-08-22T08:00:00Z"));
        saveAudit(
                otherActor,
                "OTHER",
                Instant.parse("2026-08-23T08:00:00Z"));
        String token = login(adminUsername);

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("page", "0")
                        .param("size", "2")
                        .param("actorUsername", "  " + filterActor + "  ")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id")
                        .value(newest.getId().toString()))
                .andExpect(jsonPath("$.content[0].actorUsername")
                        .value(filterActor))
                .andExpect(jsonPath("$.content[1].id")
                        .value(middle.getId().toString()));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("page", "1")
                        .param("size", "2")
                        .param("actorUsername", filterActor)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id")
                        .value(oldest.getId().toString()))
                .andExpect(jsonPath("$.content[0].actorUsername")
                        .value(filterActor));
    }

    @Test
    void tradingAgentCannotGetAuditLogs() throws Exception {
        String token = login(agentUsername);

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void idempotentTradeEvaluationCreatesOneAuditLog() throws Exception {
        String token = login(agentUsername);
        String idempotencyKey = "audit-" + UUID.randomUUID();
        String requestBody = objectMapper.writeValueAsString(standardRequest());

        MvcResult firstResult = mockMvc.perform(post("/api/v1/access/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(IDEMPOTENCY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult secondResult = mockMvc.perform(post("/api/v1/access/evaluate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(IDEMPOTENCY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstResponse = objectMapper.readTree(
                firstResult.getResponse().getContentAsString());
        JsonNode secondResponse = objectMapper.readTree(
                secondResult.getResponse().getContentAsString());
        UUID requestId = UUID.fromString(firstResponse.get("id").asText());

        assertThat(secondResponse.get("id").asText())
                .isEqualTo(firstResponse.get("id").asText());

        List<AuditLogEntity> auditLogs = auditLogRepository
                .findByRequestId(requestId, Pageable.unpaged())
                .getContent();
        assertThat(auditLogs).singleElement().satisfies(audit -> {
            assertThat(audit.getActorUsername()).isEqualTo(agentUsername);
            assertThat(audit.getAction())
                    .isEqualTo(AuditLogService.TRADE_EVALUATION);
            assertThat(audit.getPreviousState()).isNull();
            assertThat(audit.getNewState()).isEqualTo("APPROVED");
            assertThat(audit.getDetails()).isEqualTo(ReasonCode.EXEC_PASS_STANDARD);
            assertThat(audit.getTimestamp()).isNotNull();
        });
        assertThat(accessRequestRepository
                .findByAgentId(agentId, Pageable.unpaged())
                .getTotalElements())
                .isOne();
    }

    @Test
    void manualOverrideCapturesPreviousAndNewState() throws Exception {
        UUID requestId = createAccessRequest(DecisionResult.MANUAL_REVIEW);
        String token = login(adminUsername);
        AdminOverrideRequest overrideRequest = new AdminOverrideRequest(
                DecisionResult.APPROVED,
                "OVERRIDE_ADMIN_APPROVED",
                "Reviewed by admin");

        mockMvc.perform(post("/api/v1/admin/requests/{id}/override", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overrideRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPROVED"));

        List<AuditLogEntity> auditLogs = auditLogRepository
                .findByRequestId(requestId, Pageable.unpaged())
                .getContent();
        assertThat(auditLogs).singleElement().satisfies(audit -> {
            assertThat(audit.getActorUsername()).isEqualTo(adminUsername);
            assertThat(audit.getAction()).isEqualTo(AuditLogService.ADMIN_OVERRIDE);
            assertThat(audit.getPreviousState()).isEqualTo("MANUAL_REVIEW");
            assertThat(audit.getNewState()).isEqualTo("APPROVED");
            assertThat(audit.getDetails()).isEqualTo("Reviewed by admin");
        });
    }

    @Test
    void agentStatusUpdateCreatesAuditWithoutRequestId() throws Exception {
        String token = login(adminUsername);
        UpdateAgentStatusRequest statusRequest =
                new UpdateAgentStatusRequest(AgentStatus.SUSPENDED);

        mockMvc.perform(patch("/api/v1/admin/agents/{id}/status", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        List<AuditLogEntity> statusAudits = auditLogRepository
                .findByActorUsername(adminUsername, Pageable.unpaged())
                .getContent()
                .stream()
                .filter(audit -> AuditLogService.AGENT_STATUS_CHANGE
                        .equals(audit.getAction()))
                .toList();

        assertThat(statusAudits).singleElement().satisfies(audit -> {
            assertThat(audit.getRequestId()).isNull();
            assertThat(audit.getPreviousState()).isEqualTo("ACTIVE");
            assertThat(audit.getNewState()).isEqualTo("SUSPENDED");
            assertThat(audit.getDetails()).isEqualTo("Agent ID: " + agentId);
        });
        assertThat(userRepository.findById(agentUserId)
                .orElseThrow()
                .getStatus())
                .isEqualTo(AgentStatus.SUSPENDED);
    }

    private AuditLogEntity saveAudit(
            String actorUsername,
            String newState,
            Instant timestamp) {

        AuditLogEntity savedAudit = newTransaction().execute(status -> {
            AuditLogEntity audit = new AuditLogEntity(
                    null,
                    actorUsername,
                    AuditLogService.AGENT_STATUS_CHANGE,
                    "OLD",
                    newState,
                    null);
            audit.setTimestamp(timestamp);
            return auditLogRepository.saveAndFlush(audit);
        });

        if (savedAudit == null) {
            throw new IllegalStateException("Could not create audit log");
        }
        return savedAudit;
    }

    private UUID createAccessRequest(DecisionResult outcome) {
        UUID savedRequestId = newTransaction().execute(status -> {
            TradingAgentEntity agent = tradingAgentRepository.findById(agentId)
                    .orElseThrow();
            AccessRequestEntity request = new AccessRequestEntity(
                    agent,
                    "AAPL",
                    TradeType.BUY,
                    new BigDecimal("1500000.00"),
                    new BigDecimal("0.50"),
                    outcome,
                    ReasonCode.FLAG_HIGH_VOL_RISK,
                    null);
            return accessRequestRepository.saveAndFlush(request).getId();
        });

        if (savedRequestId == null) {
            throw new IllegalStateException("Could not create access request");
        }
        return savedRequestId;
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

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString());
        return response.get("token").asText();
    }

    private TransactionTemplate newTransaction() {
        return new TransactionTemplate(transactionManager);
    }
}
