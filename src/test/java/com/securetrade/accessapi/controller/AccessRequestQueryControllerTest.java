package com.securetrade.accessapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.DecisionResult;
import com.securetrade.accessapi.common.enums.TradeType;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.decision.ReasonCode;
import com.securetrade.accessapi.dto.request.LoginRequest;
import com.securetrade.accessapi.entity.AccessRequestEntity;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.entity.UserEntity;
import com.securetrade.accessapi.repository.AccessRequestRepository;
import com.securetrade.accessapi.repository.TradingAgentRepository;
import com.securetrade.accessapi.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessRequestQueryControllerTest {

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
    private EntityManager entityManager;

    private String encodedPassword;

    @BeforeAll
    void encodePassword() {
        encodedPassword = passwordEncoder.encode(PASSWORD);
    }

    @Test
    void agentCanGetOwnPaginatedRequests() throws Exception {
        TradingAgentEntity agent = createAgent();
        TradingAgentEntity otherAgent = createAgent();
        AccessRequestEntity oldest = saveRequest(
                agent, "AAPL", Instant.parse("2026-08-20T08:00:00Z"));
        AccessRequestEntity middle = saveRequest(
                agent, "MSFT", Instant.parse("2026-08-21T08:00:00Z"));
        AccessRequestEntity newest = saveRequest(
                agent, "NVDA", Instant.parse("2026-08-22T08:00:00Z"));
        saveRequest(otherAgent, "TSLA", Instant.parse("2026-08-23T08:00:00Z"));
        String token = login(agent.getUser().getUsername());

        mockMvc.perform(get("/api/v1/access/requests/me")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(newest.getId().toString()))
                .andExpect(jsonPath("$.content[0].agentId").value(agent.getId().toString()))
                .andExpect(jsonPath("$.content[1].id").value(middle.getId().toString()))
                .andExpect(jsonPath("$.content[1].agentId").value(agent.getId().toString()));

        mockMvc.perform(get("/api/v1/access/requests/me")
                        .param("page", "1")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(oldest.getId().toString()))
                .andExpect(jsonPath("$.content[0].agentId").value(agent.getId().toString()));
    }

    @Test
    void ownerAgentCanGetRequest() throws Exception {
        TradingAgentEntity agent = createAgent();
        AccessRequestEntity request = saveRequest(agent, "AAPL", Instant.now());
        String token = login(agent.getUser().getUsername());

        mockMvc.perform(get("/api/v1/access/requests/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(request.getId().toString()))
                .andExpect(jsonPath("$.agentId").value(agent.getId().toString()))
                .andExpect(jsonPath("$.agentCode").value(agent.getAgentCode()));
    }

    @Test
    void nonOwnerAgentCannotGetRequest() throws Exception {
        TradingAgentEntity owner = createAgent();
        TradingAgentEntity otherAgent = createAgent();
        AccessRequestEntity request = saveRequest(owner, "AAPL", Instant.now());
        String token = login(otherAgent.getUser().getUsername());

        mockMvc.perform(get("/api/v1/access/requests/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanGetAnyRequest() throws Exception {
        TradingAgentEntity owner = createAgent();
        AccessRequestEntity request = saveRequest(owner, "AAPL", Instant.now());
        UserEntity admin = createUser(UserRole.ADMIN);
        String token = login(admin.getUsername());

        mockMvc.perform(get("/api/v1/access/requests/{id}", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(request.getId().toString()))
                .andExpect(jsonPath("$.agentId").value(owner.getId().toString()));
    }

    @Test
    void missingRequestReturnsNotFound() throws Exception {
        TradingAgentEntity agent = createAgent();
        String token = login(agent.getUser().getUsername());

        mockMvc.perform(get("/api/v1/access/requests/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/access/requests/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidPaginationReturnsBadRequest() throws Exception {
        TradingAgentEntity agent = createAgent();
        String token = login(agent.getUser().getUsername());

        mockMvc.perform(get("/api/v1/access/requests/me")
                        .param("page", "-1")
                        .param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestLookupLoadsOwnerData() {
        TradingAgentEntity agent = createAgent();
        AccessRequestEntity request = saveRequest(agent, "AAPL", Instant.now());
        UUID requestId = request.getId();
        entityManager.flush();
        entityManager.clear();

        AccessRequestEntity loadedRequest = accessRequestRepository.findById(requestId)
                .orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager
                .getEntityManagerFactory()
                .getPersistenceUnitUtil();

        assertThat(persistenceUnitUtil.isLoaded(loadedRequest, "agent")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(loadedRequest.getAgent(), "user")).isTrue();
    }

    private TradingAgentEntity createAgent() {
        UserEntity user = createUser(UserRole.TRADING_AGENT);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        TradingAgentEntity agent = new TradingAgentEntity(
                user,
                "QR-" + suffix.substring(0, 12),
                "Query Agent",
                "MOMENTUM",
                new BigDecimal("5000000.00"));
        return tradingAgentRepository.saveAndFlush(agent);
    }

    private UserEntity createUser(UserRole role) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        UserEntity user = new UserEntity(
                "query_" + suffix,
                encodedPassword,
                role,
                AgentStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private AccessRequestEntity saveRequest(
            TradingAgentEntity agent,
            String symbol,
            Instant createdAt) {

        AccessRequestEntity request = new AccessRequestEntity(
                agent,
                symbol,
                TradeType.BUY,
                new BigDecimal("1000.00"),
                new BigDecimal("0.20"),
                DecisionResult.APPROVED,
                ReasonCode.EXEC_PASS_STANDARD,
                null);
        request.setCreatedAt(createdAt);
        return accessRequestRepository.saveAndFlush(request);
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
}
