package com.hasancetin.platform.auth.application;

import java.util.Locale;

import com.hasancetin.platform.auth.api.AuthDtos.RegisterResponse;
import com.hasancetin.platform.auth.api.AuthDtos.TokenResponse;
import com.hasancetin.platform.auth.domain.UserAccount;
import com.hasancetin.platform.auth.domain.UserAccountRepository;
import com.hasancetin.platform.common.events.AuthEvents.UserRegistered;
import com.hasancetin.platform.common.events.Topics;
import com.hasancetin.platform.common.outbox.OutboxService;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final OutboxService outbox;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                       OutboxService outbox) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.outbox = outbox;
    }

    @Transactional
    public RegisterResponse register(String email, String rawPassword) {
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        UserAccount user = users.save(new UserAccount(normalized, passwordEncoder.encode(rawPassword)));
        outbox.publish(Topics.AUTH_EVENTS, UserRegistered.of(user.getId().toString(), user.getEmail()));
        return new RegisterResponse(user.getId(), user.getEmail());
    }

    @Transactional(readOnly = true)
    public TokenResponse login(String email, String rawPassword) {
        UserAccount user = users.findByEmail(normalize(email))
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                // same answer for "unknown email" and "wrong password": do not reveal which accounts exist
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return new TokenResponse(tokens.issue(user), "Bearer", tokens.ttl().toSeconds());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
