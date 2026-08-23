package com.securetrade.accessapi.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenProviderTest {

    private static final String TEST_SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private JwtTokenProvider jwtTokenProvider;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, 60_000);
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("test-agent");
    }

    @Test
    void shouldGenerateToken() {
        String token = jwtTokenProvider.generateToken(authentication);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void shouldReadUsernameFromToken() {
        String token = jwtTokenProvider.generateToken(authentication);

        assertThat(jwtTokenProvider.getUsernameFromJwt(token)).isEqualTo("test-agent");
    }

    @Test
    void shouldValidateToken() {
        String token = jwtTokenProvider.generateToken(authentication);

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.validateToken(token + "bad")).isFalse();
    }

    @Test
    void shouldRejectTokenWithoutUsername() {
        String token = Jwts.builder()
                .setExpiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET)))
                .compact();

        assertThat(jwtTokenProvider.validateToken(token)).isFalse();
    }

    @Test
    void shouldRejectInvalidExpirationSetting() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtTokenProvider(TEST_SECRET, 0));
    }
}
