package com.securetrade.accessapi.dto.response;

import com.securetrade.accessapi.common.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Successful login result")
public class AuthResponse {

    @Schema(description = "Signed JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private final String token;

    @Schema(description = "Token type used in the Authorization header", example = "Bearer")
    private final String tokenType;

    @Schema(description = "Authenticated username", example = "agent.one")
    private final String username;

    @Schema(
            description = "User role",
            example = "TRADING_AGENT",
            allowableValues = {"ADMIN", "TRADING_AGENT"})
    private final UserRole role;

    public AuthResponse(String token, String tokenType, String username, UserRole role) {
        this.token = token;
        this.tokenType = tokenType;
        this.username = username;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getUsername() {
        return username;
    }

    public UserRole getRole() {
        return role;
    }
}
