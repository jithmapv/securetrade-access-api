package com.securetrade.accessapi.service;

import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.common.exception.InvalidRequestException;
import com.securetrade.accessapi.common.exception.ResourceNotFoundException;
import com.securetrade.accessapi.dto.request.LoginRequest;
import com.securetrade.accessapi.dto.response.AuthResponse;
import com.securetrade.accessapi.security.jwt.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String ROLE_PREFIX = "ROLE_";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider) {

        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public AuthResponse login(LoginRequest request) {
        // Check username and password
        Authentication authentication;

        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()));
        } catch (ResourceNotFoundException exception) {
            throw new BadCredentialsException("Invalid username or password", exception);
        }

        String token = jwtTokenProvider.generateToken(authentication);
        UserRole role = getRole(authentication);

        return new AuthResponse(token, "Bearer", authentication.getName(), role);
    }

    private UserRole getRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .map(UserRole::valueOf)
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("User role is missing"));
    }
}
