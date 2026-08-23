package com.securetrade.accessapi.dto.response;

import com.securetrade.accessapi.common.enums.UserRole;

public class AuthResponse {

    private final String token;
    private final String tokenType;
    private final String username;
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
