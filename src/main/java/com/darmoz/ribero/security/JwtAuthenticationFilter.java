package com.darmoz.ribero.security;

import com.darmoz.ribero.service.ShadowUserService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autentica requests a partir de un access token JWT (Authorization: Bearer ...)
 * emitido por darmoz-auth. No rechaza requests con token ausente/invalido:
 * solo deja el SecurityContext vacio y es authorizeHttpRequests() el que
 * decide si la ruta requiere autenticacion.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerificationService jwtVerificationService;
    private final ShadowUserService shadowUserService;

    public JwtAuthenticationFilter(JwtVerificationService jwtVerificationService, ShadowUserService shadowUserService) {
        this.jwtVerificationService = jwtVerificationService;
        this.shadowUserService = shadowUserService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtVerificationService.VerifiedToken verified = jwtVerificationService.verify(token);

                List<GrantedAuthority> authorities = verified.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .map(GrantedAuthority.class::cast)
                        .toList();
                var principal = new AuthUser(verified.userId(), verified.email(), verified.roles());
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                try {
                    shadowUserService.upsert(verified.userId(), verified.email(), verified.roles());
                } catch (RuntimeException e) {
                    // El shadow user es un cache best-effort (nombre/roles para listados
                    // admin); un problema al sincronizarlo no debe tumbar una request que
                    // ya se autentico correctamente.
                    log.warn("No se pudo sincronizar shadow user para {}: {}", verified.userId(), e.getMessage());
                }
            } catch (JwtException ignored) {
                // token invalido/expirado/malformado: se sigue sin autenticacion
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorizationHeader.substring(BEARER_PREFIX.length());
    }
}
