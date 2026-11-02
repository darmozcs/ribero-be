package com.darmoz.ribero.controller;

import com.darmoz.ribero.dto.response.LocalResponse;
import com.darmoz.ribero.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Administracion de locales y de que vendedor entra a que local. Los usuarios en si (alta,
 * baja, contraseña) se gestionan en darmoz-auth via /auth/apps/api/users.
 */
@RestController
@RequestMapping("/api/rb/admin")
@PreAuthorize("hasRole('APP_ADMIN')")
public class AdminController {

    private final JdbcClient jdbc;

    public AdminController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/locales")
    public List<LocalResponse> locales() {
        return jdbc.sql("SELECT id, nombre, direccion, activo FROM rb_locales ORDER BY activo DESC, nombre")
                .query(LocalResponse.class)
                .list();
    }

    @PostMapping("/locales")
    @ResponseStatus(HttpStatus.CREATED)
    public LocalResponse crearLocal(@Valid @RequestBody LocalRequest request) {
        return jdbc.sql("""
                        INSERT INTO rb_locales (nombre, direccion, activo) VALUES (:nombre, :direccion, :activo)
                        RETURNING id, nombre, direccion, activo
                        """)
                .param("nombre", request.nombre().trim())
                .param("direccion", request.direccion())
                .param("activo", request.activo() == null || request.activo())
                .query(LocalResponse.class)
                .single();
    }

    /** Un local no se borra (tiene ventas e ingresos): se desactiva con activo=false. */
    @PutMapping("/locales/{id}")
    public LocalResponse actualizarLocal(@PathVariable UUID id, @Valid @RequestBody LocalRequest request) {
        return jdbc.sql("""
                        UPDATE rb_locales SET nombre = :nombre, direccion = :direccion, activo = :activo
                        WHERE id = :id
                        RETURNING id, nombre, direccion, activo
                        """)
                .param("id", id)
                .param("nombre", request.nombre().trim())
                .param("direccion", request.direccion())
                .param("activo", request.activo() == null || request.activo())
                .query(LocalResponse.class)
                .optional()
                .orElseThrow(() -> new NotFoundException("Local inexistente"));
    }

    @GetMapping("/asignaciones")
    public Asignacion asignacion(@RequestParam String email) {
        List<UUID> localIds = jdbc.sql("SELECT local_id FROM rb_user_locales WHERE email = lower(:email)")
                .param("email", email)
                .query(UUID.class)
                .list();
        return new Asignacion(email, localIds);
    }

    /** Reemplaza todos los locales asignados al usuario. */
    @PutMapping("/asignaciones")
    @Transactional
    public Asignacion asignar(@Valid @RequestBody Asignacion request) {
        jdbc.sql("DELETE FROM rb_user_locales WHERE email = lower(:email)")
                .param("email", request.email())
                .update();
        for (UUID localId : request.localIds()) {
            jdbc.sql("""
                            INSERT INTO rb_user_locales (email, local_id) VALUES (lower(:email), :localId)
                            ON CONFLICT DO NOTHING
                            """)
                    .param("email", request.email())
                    .param("localId", localId)
                    .update();
        }
        return asignacion(request.email());
    }

    public record LocalRequest(@NotBlank @Size(max = 120) String nombre, @Size(max = 255) String direccion,
                               Boolean activo) {
    }

    public record Asignacion(@NotBlank @Email String email, @NotNull List<UUID> localIds) {
    }
}
