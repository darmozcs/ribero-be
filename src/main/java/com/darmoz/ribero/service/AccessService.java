package com.darmoz.ribero.service;

import com.darmoz.ribero.dto.response.LocalResponse;
import com.darmoz.ribero.security.AuthUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Que locales puede usar cada usuario: el administrador (APP_ADMIN) todos los activos, el
 * vendedor solo los que el administrador le asigno en rb_user_locales.
 */
@Service
public class AccessService {

    private final JdbcClient jdbc;

    public AccessService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<LocalResponse> localesPermitidos(AuthUser user) {
        if (user.isAdmin()) {
            return jdbc.sql("SELECT id, nombre, direccion, activo FROM rb_locales WHERE activo ORDER BY nombre")
                    .query(LocalResponse.class)
                    .list();
        }
        return jdbc.sql("""
                        SELECT l.id, l.nombre, l.direccion, l.activo
                        FROM rb_locales l
                        JOIN rb_user_locales ul ON ul.local_id = l.id
                        WHERE l.activo AND ul.email = lower(:email)
                        ORDER BY l.nombre
                        """)
                .param("email", user.email())
                .query(LocalResponse.class)
                .list();
    }

    public boolean puedeOperar(AuthUser user, UUID localId) {
        if (user.isAdmin()) {
            return jdbc.sql("SELECT EXISTS (SELECT 1 FROM rb_locales WHERE id = :id AND activo)")
                    .param("id", localId)
                    .query(Boolean.class)
                    .single();
        }
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM rb_user_locales ul JOIN rb_locales l ON l.id = ul.local_id
                            WHERE ul.local_id = :id AND ul.email = lower(:email) AND l.activo)
                        """)
                .param("id", localId)
                .param("email", user.email())
                .query(Boolean.class)
                .single();
    }

    public void exigirAcceso(AuthUser user, UUID localId) {
        if (!puedeOperar(user, localId)) {
            throw new AccessDeniedException("Sin acceso al local " + localId);
        }
    }
}
