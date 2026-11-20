package com.bankflow.auth.application.token;

import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.infrastructure.security.jwt.JwtProperties;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Émet les JWT d'accès, signés en RS256 et de courte durée. */
@Service
public class AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issue(User user) {
        var now = clock.instant();
        var claims =
                JwtClaimsSet.builder()
                        .id(UUID.randomUUID().toString())
                        .issuer(properties.issuer())
                        .audience(List.of(properties.audience()))
                        .subject(user.getId().toString())
                        .claim("role", user.getRole().name())
                        .issuedAt(now)
                        .expiresAt(now.plus(properties.accessTokenTtl()))
                        .build();
        // L'encodeur ajoute le kid de la clé de signature, éphémère ou fournie
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        var token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims));
        return new AccessToken(token.getTokenValue(), properties.accessTokenTtl().toSeconds());
    }
}
