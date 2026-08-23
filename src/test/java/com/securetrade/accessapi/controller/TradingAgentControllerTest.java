package com.securetrade.accessapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.dto.request.CreateAgentRequest;
import com.securetrade.accessapi.dto.request.LoginRequest;
import com.securetrade.accessapi.dto.request.UpdateAgentStatusRequest;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.entity.UserEntity;
import com.securetrade.accessapi.repository.TradingAgentRepository;
import com.securetrade.accessapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TradingAgentControllerTest {

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
    private PasswordEncoder passwordEncoder;

    @Test
    void tradingAgentCannotCreateAgent() throws Exception {
        UserEntity caller = createUser("agent_", UserRole.TRADING_AGENT);
        String token = login(caller.getUsername());
        CreateAgentRequest request = createRequest();

        mockMvc.perform(post("/api/v1/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        assertThat(userRepository.existsByUsername(request.getUsername())).isFalse();
        assertThat(tradingAgentRepository.existsByAgentCode(request.getAgentCode())).isFalse();
    }

    @Test
    void adminCanCreateGetAndUpdateAgent() throws Exception {
        UserEntity admin = createUser("admin_", UserRole.ADMIN);
        String token = login(admin.getUsername());
        CreateAgentRequest request = createRequest();

        MvcResult createResult = mockMvc.perform(post("/api/v1/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.username").value(request.getUsername()))
                .andExpect(jsonPath("$.agentCode").value(request.getAgentCode()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("TRADING_AGENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        JsonNode response = objectMapper.readTree(
                createResult.getResponse().getContentAsString());
        UUID agentId = UUID.fromString(response.get("id").asText());

        UserEntity createdUser = userRepository.findByUsername(request.getUsername())
                .orElseThrow();
        assertThat(passwordEncoder.matches(request.getPassword(), createdUser.getPasswordHash()))
                .isTrue();
        assertThat(createdUser.getRole()).isEqualTo(UserRole.TRADING_AGENT);

        TradingAgentEntity createdAgent = tradingAgentRepository
                .findByAgentCode(request.getAgentCode())
                .orElseThrow();
        assertThat(createdAgent.getUser().getId()).isEqualTo(createdUser.getId());

        mockMvc.perform(get("/api/v1/admin/agents/{id}", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentCode").value(request.getAgentCode()));

        UpdateAgentStatusRequest statusRequest =
                new UpdateAgentStatusRequest(AgentStatus.SUSPENDED);

        mockMvc.perform(patch("/api/v1/admin/agents/{id}/status", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        assertThat(createdUser.getStatus()).isEqualTo(AgentStatus.SUSPENDED);
    }

    @Test
    void adminCannotCreateAgentWithTooLongCode() throws Exception {
        UserEntity admin = createUser("admin_", UserRole.ADMIN);
        String token = login(admin.getUsername());
        CreateAgentRequest request = createRequest();
        request.setAgentCode("A".repeat(51));

        mockMvc.perform(post("/api/v1/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.existsByUsername(request.getUsername())).isFalse();
    }

    @Test
    void agentCanGetOwnProfile() throws Exception {
        createAgentProfile("other_");
        TradingAgentEntity ownProfile = createAgentProfile("self_");
        String token = login(ownProfile.getUser().getUsername());

        mockMvc.perform(get("/api/v1/agents/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownProfile.getId().toString()))
                .andExpect(jsonPath("$.userId").value(ownProfile.getUser().getId().toString()))
                .andExpect(jsonPath("$.username").value(ownProfile.getUser().getUsername()))
                .andExpect(jsonPath("$.agentCode").value(ownProfile.getAgentCode()))
                .andExpect(jsonPath("$.name").value(ownProfile.getName()))
                .andExpect(jsonPath("$.strategyType").value(ownProfile.getStrategyType()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("TRADING_AGENT"));
    }

    private UserEntity createUser(String prefix, UserRole role) {
        String username = prefix + UUID.randomUUID().toString().replace("-", "");
        UserEntity user = new UserEntity(
                username,
                passwordEncoder.encode(PASSWORD),
                role,
                AgentStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private TradingAgentEntity createAgentProfile(String prefix) {
        UserEntity user = createUser(prefix, UserRole.TRADING_AGENT);
        TradingAgentEntity agent = new TradingAgentEntity(
                user,
                newAgentCode(),
                "Test Agent",
                "MOMENTUM",
                new BigDecimal("100000.00"));
        return tradingAgentRepository.saveAndFlush(agent);
    }

    private CreateAgentRequest createRequest() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        return new CreateAgentRequest(
                "new_" + suffix,
                PASSWORD,
                "AGT-" + suffix.substring(0, 12),
                "New Trading Agent",
                "MEAN_REVERSION",
                new BigDecimal("500000.00"));
    }

    private String newAgentCode() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        return "AGT-" + suffix.substring(0, 12);
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
