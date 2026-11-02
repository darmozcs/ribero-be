package com.darmoz.ribero.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lote de operaciones que el celular tenia pendientes, en el orden en que se hicieron. El
 * payload depende del tipo (ver los records de abajo) y se convierte en SyncService.
 */
public record SyncRequest(@NotNull UUID deviceId, @NotNull @Size(max = 200) List<@Valid Op> ops) {

    public enum OpType { INGRESO, VENTA, ANULACION, CIERRE }

    public record Op(@NotNull UUID opId, @NotNull OpType type, @NotNull JsonNode payload) {
    }

    public record Ingreso(UUID localId, Instant createdAt, List<IngresoItem> items) {
    }

    /** Nombre y precio vienen de la ficha del producto que se completo en el ingreso. */
    public record IngresoItem(String barcode, String format, String nombre, BigDecimal precio, int cantidad) {
    }

    public record Venta(UUID localId, String numero, String formaPago, BigDecimal total, Instant createdAt,
                        List<VentaItem> items) {
    }

    public record VentaItem(String barcode, String nombre, BigDecimal precio, int cantidad) {
    }

    public record Anulacion(UUID localId, UUID ventaId, String motivo, Instant createdAt) {
    }

    public record Cierre(UUID localId, Instant desde, Instant hasta, int cantidadVentas, BigDecimal totalVentas,
                         int cantidadAnuladas, BigDecimal totalAnulado, BigDecimal neto,
                         Map<String, BigDecimal> porFormaPago) {
    }
}
