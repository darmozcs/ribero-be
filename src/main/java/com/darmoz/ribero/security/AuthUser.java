package com.darmoz.ribero.security;

import java.util.Set;
import java.util.UUID;

/**
 * Principal de cada request autenticado, armado desde los claims del JWT.
 * Las tablas de ribero identifican al usuario por email (estable aunque
 * darmoz-auth reemita el user_id), por eso viaja junto al userId.
 */
public record AuthUser(UUID userId, String email, Set<String> roles) {

    public static final String ROLE_ADMIN = "APP_ADMIN";

    public boolean isAdmin() {
        return roles.contains(ROLE_ADMIN);
    }
}
