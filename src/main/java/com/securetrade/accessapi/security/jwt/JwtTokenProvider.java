package com.securetrade.accessapi.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final long expirationMs;
    private final SecretKey signingKey;
    private final JwtParser jwtParser;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {

        if (expirationMs <= 0) {
            throw new IllegalArgumentException("JWT expiration must be greater than zero");
        }

        this.expirationMs = expirationMs;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.jwtParser = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build();
    }

    public String generateToken(Authentication authentication) {
        // Create JWT token for user
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expirationMs);

        return Jwts.builder()
                .setSubject(authentication.getName())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public String getUsernameFromJwt(String token) {
        return getClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = getClaims(token);
            return StringUtils.hasText(claims.getSubject());
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return jwtParser.parseClaimsJws(token).getBody();
    }
}
