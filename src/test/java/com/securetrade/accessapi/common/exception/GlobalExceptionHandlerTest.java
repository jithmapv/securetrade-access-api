package com.securetrade.accessapi.common.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.dto.request.CreateAgentRequest;
import com.securetrade.accessapi.dto.request.LoginRequest;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.entity.UserEntity;
import com.securetrade.accessapi.repository.TradingAgentRepository;
import com.securetrade.accessapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GlobalExceptionHandlerTest {

    private static final String ERROR_BASE = "https://api.securetrade.com/errors";
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

    private String adminUsername;
    private String agentUsername;
    private String existingAgentCode;

    @BeforeEach
    void createUsersAndAgent() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        adminUsername = "error_admin_" + suffix;
        agentUsername = "error_agent_" + suffix;
        existingAgentCode = "ERR-" + suffix.substring(0, 12);

        UserEntity admin = new UserEntity(
                adminUsername,
                passwordEncoder.encode(PASSWORD),
                UserRole.ADMIN,
                AgentStatus.ACTIVE);
        userRepository.saveAndFlush(admin);

        UserEntity agentUser = new UserEntity(
                agentUsername,
                passwordEncoder.encode(PASSWORD),
                UserRole.TRADING_AGENT,
                AgentStatus.ACTIVE);
        agentUser = userRepository.saveAndFlush(agentUser);

        TradingAgentEntity agent = new TradingAgentEntity(
                agentUser,
                existingAgentCode,
                "Error Test Agent",
                "MOMENTUM",
                new BigDecimal("1000000.00"));
        tradingAgentRepository.saveAndFlush(agent);
    }

    @Test
    void invalidLoginRequestReturnsValidationProblem() throws Exception {
        LoginRequest request = new LoginRequest("", "");

        expectProblem(
                mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))),
                HttpStatus.BAD_REQUEST,
                ERROR_BASE + "/invalid-request",
                "Invalid Request",
                "Request validation failed",
                "/api/v1/auth/login",
                "INVALID_REQUEST")
                .andExpect(jsonPath("$.validationErrors.username")
                        .value("Username is required"))
                .andExpect(jsonPath("$.validationErrors.password")
                        .value("Password is required"))
                .andExpect(jsonPath("$.validationErrors.length()").value(2));
    }

    @Test
    void missingAgentReturnsResourceNotFoundProblem() throws Exception {
        String token = login(adminUsername);
        UUID missingAgentId = UUID.randomUUID();

        expectProblemWithoutValidationErrors(
                mockMvc.perform(get("/api/v1/admin/agents/{id}", missingAgentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)),
                HttpStatus.NOT_FOUND,
                ERROR_BASE + "/resource-not-found",
                "Resource Not Found",
                "Agent profile not found",
                "/api/v1/admin/agents/" + missingAgentId,
                "RESOURCE_NOT_FOUND");
    }

    @Test
    void duplicateAgentCodeReturnsConflictProblem() throws Exception {
        String token = login(adminUsername);
        String username = "new_agent_" + UUID.randomUUID()
                .toString()
                .replace("-", "");
        CreateAgentRequest request = new CreateAgentRequest(
                username,
                PASSWORD,
                existingAgentCode,
                "New Error Test Agent",
                "MEAN_REVERSION",
                new BigDecimal("500000.00"));

        expectProblemWithoutValidationErrors(
                mockMvc.perform(post("/api/v1/admin/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))),
                HttpStatus.CONFLICT,
                ERROR_BASE + "/duplicate-resource",
                "Duplicate Resource",
                "Agent code already exists",
                "/api/v1/admin/agents",
                "DUPLICATE_RESOURCE");
    }

    @Test
    void wrongRoleReturnsAccessDeniedProblem() throws Exception {
        String token = login(agentUsername);

        expectProblemWithoutValidationErrors(
                mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)),
                HttpStatus.FORBIDDEN,
                ERROR_BASE + "/access-denied",
                "Access Denied",
                "User is not authorized to access this resource",
                "/api/v1/admin/audit-logs",
                "ACCESS_DENIED");
    }

    @Test
    void badLoginReturnsUnauthorizedProblem() throws Exception {
        LoginRequest request = new LoginRequest(adminUsername, "wrong-password");

        expectProblemWithoutValidationErrors(
                mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))),
                HttpStatus.UNAUTHORIZED,
                ERROR_BASE + "/access-denied",
                "Unauthorized",
                "Invalid username or password",
                "/api/v1/auth/login",
                "ACCESS_DENIED");
    }

    private ResultActions expectProblemWithoutValidationErrors(
            ResultActions result,
            HttpStatus statusCode,
            String type,
            String title,
            String detail,
            String instance,
            String errorCode) throws Exception {

        return expectProblem(
                result,
                statusCode,
                type,
                title,
                detail,
                instance,
                errorCode)
                .andExpect(jsonPath("$.validationErrors").doesNotExist());
    }

    private ResultActions expectProblem(
            ResultActions result,
            HttpStatus statusCode,
            String type,
            String title,
            String detail,
            String instance,
            String errorCode) throws Exception {

        return result
                .andExpect(status().is(statusCode.value()))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(type))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value(statusCode.value()))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.instance").value(instance))
                .andExpect(jsonPath("$.errorCode").value(errorCode))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
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
