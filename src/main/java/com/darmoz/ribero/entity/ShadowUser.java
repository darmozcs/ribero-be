package com.darmoz.ribero.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Copia local ("shadow") de un usuario de darmoz-auth, upsertada a partir de
 * los claims del JWT en cada request autenticado. darmoz-auth no expone un
 * endpoint para listar usuarios, asi que esta tabla es la unica fuente para
 * el selector de "asignar a" del admin.
 */
@Entity
@Table(name = "rb_users")
public class ShadowUser {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /** Snapshot de roles del ultimo token visto, unidos por coma. */
    @Column(name = "roles", nullable = false)
    private String roles;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ShadowUser() {
    }

    public ShadowUser(UUID userId, String email, String roles, Instant lastSeenAt, Instant createdAt) {
        this.userId = userId;
        this.email = email;
        this.roles = roles;
        this.lastSeenAt = lastSeenAt;
        this.createdAt = createdAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getRoles() {
        return roles;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
