package com.securetrade.accessapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.DecisionResult;
import com.securetrade.accessapi.common.enums.TradeType;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.dto.request.AdminOverrideRequest;
import com.securetrade.accessapi.dto.request.LoginRequest;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.entity.UserEntity;
import com.securetrade.accessapi.repository.AccessRequestRepository;
import com.securetrade.accessapi.repository.TradingAgentRepository;
import com.securetrade.accessapi.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAccessRequestControllerTest {

    private static final String PASSWORD = "StrongTestPassword123!";

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

    private UUID adminUserId;
    private UUID agentUserId;
    private UUID agentId;
    private UUID requestId;
    private String adminUsername;
    private String agentUsername;

    @BeforeEach
    void createCommittedReviewRequest() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");

            UserEntity admin = new UserEntity(
                    "review_admin_" + suffix,
                    passwordEncoder.encode(PASSWORD),
                    UserRole.ADMIN,
                    AgentStatus.ACTIVE);
            admin = userRepository.saveAndFlush(admin);

            UserEntity agentUser = new UserEntity(
                    "review_agent_" + suffix,
                    passwordEncoder.encode(PASSWORD),
                    UserRole.TRADING_AGENT,
                    AgentStatus.ACTIVE);
            agentUser = userRepository.saveAndFlush(agentUser);

            TradingAgentEntity agent = new TradingAgentEntity(
                    agentUser,
                    "MR-" + suffix.substring(0, 12),
                    "Manual Review Agent",
                    "MOMENTUM",
                    new BigDecimal("5000000.00"));
            agent = tradingAgentRepository.saveAndFlush(agent);

            AccessRequestEntity request = new AccessRequestEntity(
                    agent,
                    "AAPL",
                    TradeType.BUY,
                    new BigDecimal("1500000.00"),
                    new BigDecimal("0.50"),
                    DecisionResult.MANUAL_REVIEW,
                    "FLAG_HIGH_VOL_RISK",
                    "review-" + suffix);
            request = accessRequestRepository.saveAndFlush(request);

            adminUserId = admin.getId();
            agentUserId = agentUser.getId();
            agentId = agent.getId();
            requestId = request.getId();
            adminUsername = admin.getUsername();
            agentUsername = agentUser.getUsername();
        });
    }

    @AfterEach
    void removeCommittedReviewRequest() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            if (requestId != null && accessRequestRepository.existsById(requestId)) {
                accessRequestRepository.deleteById(requestId);
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
    void adminCanApproveManualReviewRequest() throws Exception {
        String token = login(adminUsername);
        AdminOverrideRequest overrideRequest = new AdminOverrideRequest(
                DecisionResult.APPROVED,
                "OVERRIDE_ADMIN_APPROVED",
                "Trade was checked by admin");

        mockMvc.perform(post("/api/v1/admin/requests/{id}/override", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overrideRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.agentId").value(agentId.toString()))
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.reasonCode")
                        .value("OVERRIDE_ADMIN_APPROVED"))
                .andExpect(jsonPath("$.adminNotes").doesNotExist());

        AccessRequestEntity savedRequest = accessRequestRepository.findById(requestId)
                .orElseThrow();
        assertThat(savedRequest.getOutcome()).isEqualTo(DecisionResult.APPROVED);
        assertThat(savedRequest.getReasonCode())
                .isEqualTo("OVERRIDE_ADMIN_APPROVED");
    }

    @Test
    void tradingAgentCannotOverrideRequest() throws Exception {
        String token = login(agentUsername);
        AdminOverrideRequest overrideRequest = new AdminOverrideRequest(
                DecisionResult.REJECTED,
                "OVERRIDE_RISK_EXCEEDED",
                null);

        mockMvc.perform(post("/api/v1/admin/requests/{id}/override", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overrideRequest)))
                .andExpect(status().isForbidden());

        AccessRequestEntity savedRequest = accessRequestRepository.findById(requestId)
                .orElseThrow();
        assertThat(savedRequest.getOutcome())
                .isEqualTo(DecisionResult.MANUAL_REVIEW);
        assertThat(savedRequest.getReasonCode()).isEqualTo("FLAG_HIGH_VOL_RISK");
    }

    @Test
    void missingRequestReturnsNotFound() throws Exception {
        String token = login(adminUsername);
        AdminOverrideRequest overrideRequest = new AdminOverrideRequest(
                DecisionResult.APPROVED,
                "OVERRIDE_ADMIN_APPROVED",
                null);

        mockMvc.perform(post("/api/v1/admin/requests/{id}/override", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overrideRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void finalizedRequestCannotBeOverridden() throws Exception {
        setSavedOutcome(DecisionResult.REJECTED);
        String token = login(adminUsername);
        AdminOverrideRequest overrideRequest = new AdminOverrideRequest(
                DecisionResult.APPROVED,
                "OVERRIDE_ADMIN_APPROVED",
                null);

        mockMvc.perform(post("/api/v1/admin/requests/{id}/override", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overrideRequest)))
                .andExpect(status().isBadRequest());

        assertThat(accessRequestRepository.findById(requestId)
                .orElseThrow()
                .getOutcome())
                .isEqualTo(DecisionResult.REJECTED);
    }

    @Test
    void manualReviewTargetReturnsBadRequest() throws Exception {
        String token = login(adminUsername);
        AdminOverrideRequest overrideRequest = new AdminOverrideRequest(
                DecisionResult.MANUAL_REVIEW,
                "OVERRIDE_ADMIN_APPROVED",
                null);

        mockMvc.perform(post("/api/v1/admin/requests/{id}/override", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overrideRequest)))
                .andExpect(status().isBadRequest());

        assertThat(accessRequestRepository.findById(requestId)
                .orElseThrow()
                .getOutcome())
                .isEqualTo(DecisionResult.MANUAL_REVIEW);
    }

    @Test
    void invalidOverrideFieldsReturnBadRequest() throws Exception {
        String token = login(adminUsername);
        AdminOverrideRequest overrideRequest = new AdminOverrideRequest(
                null,
                " ",
                "N".repeat(256));

        mockMvc.perform(post("/api/v1/admin/requests/{id}/override", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overrideRequest)))
                .andExpect(status().isBadRequest());

        assertThat(accessRequestRepository.findById(requestId)
                .orElseThrow()
                .getOutcome())
                .isEqualTo(DecisionResult.MANUAL_REVIEW);
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        AdminOverrideRequest overrideRequest = new AdminOverrideRequest(
                DecisionResult.APPROVED,
                "OVERRIDE_ADMIN_APPROVED",
                null);

        mockMvc.perform(post("/api/v1/admin/requests/{id}/override", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overrideRequest)))
                .andExpect(status().isUnauthorized());
    }

    private void setSavedOutcome(DecisionResult outcome) {
        AccessRequestEntity request = accessRequestRepository.findById(requestId)
                .orElseThrow();
        request.setOutcome(outcome);
        accessRequestRepository.saveAndFlush(request);
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
}
