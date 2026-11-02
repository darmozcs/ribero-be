package com.darmoz.ribero.integration;

import io.jsonwebtoken.Jwts;

import java.security.PrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * Emite tokens de test firmados con la misma llave privada cuya pareja
 * publica se expone via {@link AbstractIntegrationTest#PUBLIC_KEY_PATH},
 * replicando el shape de claims real de darmoz-auth (ver su JwtService).
 */
public final class TestJwtFactory {

    private TestJwtFactory() {
    }

    public static String issueToken(UUID userId, String email, Set<String> roles) {
        return issueToken(userId, email, roles, null, AbstractIntegrationTest.TEST_PRIVATE_KEY, "darmoz-auth");
    }

    public static String issueToken(UUID userId, String email, Set<String> roles, UUID applicationId) {
        return issueToken(userId, email, roles, applicationId, AbstractIntegrationTest.TEST_PRIVATE_KEY, "darmoz-auth");
    }

    public static String issueToken(UUID userId, String email, Set<String> roles, UUID applicationId,
                                     PrivateKey signingKey, String issuer) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(15, ChronoUnit.MINUTES);

        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles.stream().toList())
                .claim("permissions", java.util.List.of())
                .claim("typ", "access")
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt));

        if (applicationId != null) {
            builder.claim("applicationId", applicationId.toString());
        }

        return builder.signWith(signingKey, Jwts.SIG.RS256).compact();
    }
}
