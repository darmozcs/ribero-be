package com.darmoz.ribero.controller;

import com.darmoz.ribero.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Alta de celulares. El celular genera su propio id la primera vez y lo registra aca para
 * recibir un codigo corto (C01, C02, ...) que va en el numero de cada venta: asi puede
 * numerar e imprimir tickets sin conexion sin chocar con otros celulares.
 */
@RestController
@RequestMapping("/api/rb/devices")
public class DeviceController {

    private final JdbcClient jdbc;

    public DeviceController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Idempotente: registrar dos veces el mismo id devuelve el mismo codigo. */
    @PostMapping
    public DeviceResponse register(@AuthenticationPrincipal AuthUser user,
                                   @Valid @RequestBody RegisterDeviceRequest request) {
        jdbc.sql("""
                        INSERT INTO rb_devices (id, code, nombre, created_by)
                        VALUES (:id, 'C' || lpad(nextval('rb_devices_code_seq')::text, 2, '0'), :nombre, :email)
                        ON CONFLICT (id) DO NOTHING
                        """)
                .param("id", request.id())
                .param("nombre", request.nombre())
                .param("email", user.email())
                .update();
        return jdbc.sql("SELECT id, code FROM rb_devices WHERE id = :id")
                .param("id", request.id())
                .query(DeviceResponse.class)
                .single();
    }

    public record RegisterDeviceRequest(@NotNull UUID id, @Size(max = 120) String nombre) {
    }

    public record DeviceResponse(UUID id, String code) {
    }
}
