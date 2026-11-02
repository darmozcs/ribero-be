package com.darmoz.ribero.security;

import com.darmoz.ribero.service.ShadowUserService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtVerificationService jwtVerificationService;

    @Mock
    private ShadowUserService shadowUserService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenSetsAuthenticationAndUpsertsShadowUser() throws Exception {
        UUID userId = UUID.randomUUID();
        JwtVerificationService.VerifiedToken verified = new JwtVerificationService.VerifiedToken(
                userId, "user@darmoz.com", Set.of("ADMIN", "USER"), null, Instant.now());
        when(jwtVerificationService.verify("valid-token")).thenReturn(verified);

        runFilter("Bearer valid-token");

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(((AuthUser) authentication.getPrincipal()).userId()).isEqualTo(userId);
        assertThat(authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority))
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");

        verify(shadowUserService).upsert(eq(userId), eq("user@darmoz.com"), eq(Set.of("ADMIN", "USER")));
    }

    @Test
    void missingTokenLeavesContextEmpty() throws Exception {
        runFilter(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(shadowUserService, never()).upsert(any(), any(), any());
    }

    @Test
    void malformedTokenLeavesContextEmpty() throws Exception {
        when(jwtVerificationService.verify("garbage")).thenThrow(new JwtException("malformed"));

        runFilter("Bearer garbage");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(shadowUserService, never()).upsert(any(), any(), any());
    }

    @Test
    void wrongIssuerOrBadSignatureLeavesContextEmpty() throws Exception {
        when(jwtVerificationService.verify("bad-signature")).thenThrow(new JwtException("issuer/signature invalido"));

        runFilter("Bearer bad-signature");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(shadowUserService, never()).upsert(any(), any(), any());
    }

    @Test
    void shadowUserSyncFailureDoesNotBlockAuthentication() throws Exception {
        // Reproduce el caso real: el token es valido pero el upsert del shadow
        // user choca (p.ej. email ya usado por una cuenta vieja borrada en
        // darmoz-auth). La request debe seguir autenticada igual.
        UUID userId = UUID.randomUUID();
        JwtVerificationService.VerifiedToken verified = new JwtVerificationService.VerifiedToken(
                userId, "user@darmoz.com", Set.of("USER"), null, Instant.now());
        when(jwtVerificationService.verify("valid-token")).thenReturn(verified);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"))
                .when(shadowUserService).upsert(eq(userId), eq("user@darmoz.com"), eq(Set.of("USER")));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtVerificationService, shadowUserService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(((AuthUser) authentication.getPrincipal()).userId()).isEqualTo(userId);
        verify(chain).doFilter(request, response);
    }

    private void runFilter(String authorizationHeader) throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtVerificationService, shadowUserService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);
    }
}
