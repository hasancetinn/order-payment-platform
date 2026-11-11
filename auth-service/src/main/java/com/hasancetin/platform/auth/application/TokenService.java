package com.hasancetin.platform.auth.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.hasancetin.platform.auth.domain.UserAccount;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues signed access tokens. The subject is the user id; downstream services trust it as the caller identity. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final Duration ttl;
    private final String issuer;

    public TokenService(JwtEncoder encoder,
                        @Value("${auth.token-ttl:1h}") Duration ttl,
                        @Value("${auth.issuer:order-payment-platform-auth}") String issuer) {
        this.encoder = encoder;
        this.ttl = ttl;
        this.issuer = issuer;
    }

    public Duration ttl() {
        return ttl;
    }

    public String issue(UserAccount user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole()))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
