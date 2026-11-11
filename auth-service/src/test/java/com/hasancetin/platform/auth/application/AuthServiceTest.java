package com.hasancetin.platform.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.hasancetin.platform.auth.api.AuthDtos.RegisterResponse;
import com.hasancetin.platform.auth.api.AuthDtos.TokenResponse;
import com.hasancetin.platform.auth.domain.UserAccount;
import com.hasancetin.platform.auth.domain.UserAccountRepository;
import com.hasancetin.platform.common.events.AuthEvents.UserRegistered;
import com.hasancetin.platform.common.events.Topics;
import com.hasancetin.platform.common.outbox.OutboxService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.junit.jupiter.api.Test;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceTest {

    private static final String SECRET = "unit-test-secret-key-at-least-32-bytes-long!";

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final OutboxService outbox = mock(OutboxService.class);
    private final SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final TokenService tokens =
            new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), Duration.ofMinutes(5), "test-issuer");
    private final AuthService service = new AuthService(users, new BCryptPasswordEncoder(), tokens, outbox);

    @Test
    void registerStoresHashedPasswordAndPublishesUserRegistered() {
        when(users.existsByEmail("jane@example.com")).thenReturn(false);
        when(users.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterResponse response = service.register("  Jane@Example.com ", "s3cret-pass");

        assertThat(response.email()).isEqualTo("jane@example.com");
        verify(outbox).publish(eq(Topics.AUTH_EVENTS), any(UserRegistered.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("jane@example.com", "s3cret-pass"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        verify(outbox, never()).publish(any(), any());
    }

    @Test
    void loginIssuesTokenThatDownstreamServicesCanVerify() {
        UserAccount user = new UserAccount("jane@example.com", new BCryptPasswordEncoder().encode("s3cret-pass"));
        when(users.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        TokenResponse response = service.login("jane@example.com", "s3cret-pass");

        Jwt jwt = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()
                .decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("jane@example.com");
        assertThat(jwt.getClaimAsStringList("roles")).isEqualTo(List.of("USER"));
        assertThat(response.expiresInSeconds()).isEqualTo(300);
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameUnauthorizedError() {
        UserAccount user = new UserAccount("jane@example.com", new BCryptPasswordEncoder().encode("s3cret-pass"));
        when(users.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("jane@example.com", "wrong"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
        assertThatThrownBy(() -> service.login("nobody@example.com", "whatever"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
    }
}
