package com.darmoz.ribero.security;

import com.darmoz.ribero.config.AppProperties;
import com.darmoz.ribero.config.JwtVerificationProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Verifica (nunca emite) access tokens JWT RS256 firmados por darmoz-auth.
 * Sin secreto compartido ni JWKS: la llave publica se carga de un PEM local
 * (ver {@link com.darmoz.ribero.config.RsaPublicKeyConfig}).
 */
@Service
public class JwtVerificationService {

    private static final Logger log = LoggerFactory.getLogger(JwtVerificationService.class);

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_APPLICATION_ID = "applicationId";

    private final PublicKey verificationKey;
    private final JwtVerificationProperties jwtProperties;
    private final AppProperties appProperties;

    public JwtVerificationService(PublicKey jwtVerificationKey, JwtVerificationProperties jwtProperties,
                                   AppProperties appProperties) {
        this.verificationKey = jwtVerificationKey;
        this.jwtProperties = jwtProperties;
        this.appProperties = appProperties;
    }

    @PostConstruct
    void warnIfApplicationIdNotConfigured() {
        if (isBlank(appProperties.getExpectedApplicationId())) {
            log.warn("app.expected-application-id no esta configurado: no se valida el claim "
                    + "'applicationId' de los tokens. Completar esta config cuando se provisione "
                    + "el UUID de Application correspondiente en darmoz-auth.");
        }
    }

    /**
     * Parsea y valida firma, expiracion, issuer y (si esta configurado)
     * applicationId. Lanza {@link JwtException} ante cualquier fallo.
     */
    @SuppressWarnings("unchecked")
    public VerifiedToken verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(verificationKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!jwtProperties.getIssuer().equals(claims.getIssuer())) {
            throw new JwtException("Issuer inesperado: " + claims.getIssuer());
        }

        String expectedApplicationId = appProperties.getExpectedApplicationId();
        String applicationId = claims.get(CLAIM_APPLICATION_ID, String.class);
        if (!isBlank(expectedApplicationId) && !expectedApplicationId.equals(applicationId)) {
            throw new JwtException("applicationId inesperado: " + applicationId);
        }

        UUID userId = UUID.fromString(claims.getSubject());
        String email = claims.get(CLAIM_EMAIL, String.class);
        List<String> rawRoles = claims.get(CLAIM_ROLES, List.class);
        Set<String> roles = rawRoles == null ? Set.of() : Set.copyOf(rawRoles);
        Instant expiresAt = claims.getExpiration() == null ? null : claims.getExpiration().toInstant();

        return new VerifiedToken(userId, email, roles, applicationId, expiresAt);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record VerifiedToken(UUID userId, String email, Set<String> roles, String applicationId,
                                 Instant expiresAt) {
    }
}
