package com.bankflow.auth.application.token;

import com.bankflow.auth.domain.token.RefreshToken;
import com.bankflow.auth.domain.token.RefreshTokenRepository;
import com.bankflow.auth.domain.user.User;
import com.bankflow.auth.domain.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Émet, fait tourner et révoque les refresh tokens (ADR-003). */
@Service
public class RefreshTokenService {

    public record IssuedRefreshToken(String value, Duration timeToLive) {}

    public record Rotation(User user, IssuedRefreshToken refreshToken) {}

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final Clock clock;
    private final Duration timeToLive;
    private final Duration reuseGrace;
    private final SecureRandom random = new SecureRandom();

    RefreshTokenService(
            RefreshTokenRepository tokens,
            UserRepository users,
            Clock clock,
            @Value("${bankflow.refresh.ttl}") Duration timeToLive,
            @Value("${bankflow.refresh.reuse-grace}") Duration reuseGrace) {
        this.tokens = tokens;
        this.users = users;
        this.clock = clock;
        this.timeToLive = timeToLive;
        this.reuseGrace = reuseGrace;
    }

    /** Ouvre une nouvelle famille de jetons à la connexion. */
    @Transactional
    public IssuedRefreshToken issue(UUID userId) {
        return issueInFamily(userId, UUID.randomUUID());
    }

    /**
     * Remplace le jeton présenté. La révocation d'une famille doit être validée malgré l'exception
     * levée ensuite, d'où {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        var now = clock.instant();
        var token =
                tokens.findByTokenHash(hash(rawToken))
                        .orElseThrow(InvalidRefreshTokenException::new);

        if (token.isRevoked()) {
            if (!token.isReuseSuspectedAt(now, reuseGrace)) {
                throw InvalidRefreshTokenException.concurrentRotation();
            }
            tokens.revokeFamily(token.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (token.isExpiredAt(now)) {
            throw new InvalidRefreshTokenException();
        }
        var user = users.findById(token.getUserId()).filter(User::isEnabled).orElse(null);
        if (user == null) {
            tokens.revokeFamily(token.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }

        token.revoke(now);
        return new Rotation(user, issueInFamily(user.getId(), token.getFamilyId()));
    }

    /** Termine la session associée au jeton. Un jeton inconnu est ignoré. */
    @Transactional
    public void revoke(String rawToken) {
        tokens.findByTokenHash(hash(rawToken))
                .ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    /** Les jetons expirés ne servent plus : ils sont supprimés une fois par jour. */
    @Scheduled(cron = "${bankflow.refresh.purge-cron}")
    @Transactional
    public void purgeExpired() {
        tokens.deleteExpiredBefore(clock.instant());
    }

    private IssuedRefreshToken issueInFamily(UUID userId, UUID familyId) {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        var rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var now = clock.instant();
        tokens.save(new RefreshToken(userId, familyId, hash(rawToken), now.plus(timeToLive), now));
        return new IssuedRefreshToken(rawToken, timeToLive);
    }

    private static String hash(String rawToken) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
