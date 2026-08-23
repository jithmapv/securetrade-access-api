package com.securetrade.accessapi.security.service;

import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.exception.ResourceNotFoundException;
import com.securetrade.accessapi.entity.UserEntity;
import com.securetrade.accessapi.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        // Get user from database
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        SimpleGrantedAuthority authority =
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authority)
                .disabled(user.getStatus() == AgentStatus.INACTIVE)
                .accountLocked(user.getStatus() == AgentStatus.SUSPENDED)
                .build();
    }
}
