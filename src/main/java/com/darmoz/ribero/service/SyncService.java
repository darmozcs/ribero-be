package com.darmoz.ribero.service;

import com.darmoz.ribero.dto.request.SyncRequest;
import com.darmoz.ribero.dto.response.SyncResponse;
import com.darmoz.ribero.security.AuthUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Registra las operaciones que el celular hizo (muchas veces sin conexion). Cada operacion va
 * en su propia transaccion y su id es el que genero el celular, asi que reenviarla no la
 * duplica. Ver {@link SyncResponse} para el significado de cada resultado.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    static final Set<String> FORMAS_DE_PAGO = Set.of("EFECTIVO", "DEBITO", "CREDITO", "TRANSFERENCIA");

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final AccessService accessService;

    public SyncService(JdbcClient jdbc, TransactionTemplate tx, ObjectMapper objectMapper,
                       AccessService accessService) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.accessService = accessService;
    }

    public SyncResponse sync(AuthUser user, SyncRequest request) {
        boolean deviceRegistrado = jdbc.sql("SELECT EXISTS (SELECT 1 FROM rb_devices WHERE id = :id)")
                .param("id", request.deviceId())
                .query(Boolean.class)
                .single();

        List<SyncResponse.Result> results = new ArrayList<>();
        for (SyncRequest.Op op : request.ops()) {
            if (!deviceRegistrado) {
                results.add(result(op, SyncResponse.RECHAZADA, "Celular no registrado"));
                continue;
            }
            try {
                String status = tx.execute(s -> apply(user, request.deviceId(), op));
                results.add(result(op, status, null));
            } catch (Rechazo e) {
                results.add(result(op, SyncResponse.RECHAZADA, e.getMessage()));
            } catch (DuplicateKeyException e) {
                // Dos envios simultaneos de la misma operacion: el otro ya la registro.
                results.add(result(op, SyncResponse.DUPLICADA, null));
            } catch (RuntimeException e) {
                log.error("Error al sincronizar la operacion {} ({})", op.opId(), op.type(), e);
                results.add(result(op, SyncResponse.ERROR, "Error interno, se reintenta"));
                break;
            }
        }
        return new SyncResponse(results);
    }

    private String apply(AuthUser user, UUID deviceId, SyncRequest.Op op) {
        return switch (op.type()) {
            case INGRESO -> ingreso(user, deviceId, op.opId(), payload(op, SyncRequest.Ingreso.class));
            case VENTA -> venta(user, deviceId, op.opId(), payload(op, SyncRequest.Venta.class));
            case ANULACION -> anulacion(user, deviceId, op.opId(), payload(op, SyncRequest.Anulacion.class));
            case CIERRE -> cierre(user, deviceId, op.opId(), payload(op, SyncRequest.Cierre.class));
        };
    }

    private String ingreso(AuthUser user, UUID deviceId, UUID id, SyncRequest.Ingreso p) {
        if (exists("rb_ingresos", id)) {
            return SyncResponse.DUPLICADA;
        }
        exigirLocal(user, p.localId());
        requerir(p.createdAt() != null, "Falta la fecha del ingreso");
        requerir(p.items() != null && !p.items().isEmpty(), "El ingreso no tiene productos");

        // Un mismo codigo puede venir en varias filas: se suman las cantidades y queda el
        // ultimo nombre/precio cargado.
        Map<String, SyncRequest.IngresoItem> porCodigo = new LinkedHashMap<>();
        for (SyncRequest.IngresoItem item : p.items()) {
            requerir(item.barcode() != null && !item.barcode().isBlank(), "Producto sin codigo");
            requerir(item.nombre() != null && !item.nombre().isBlank(), "Producto sin nombre: " + item.barcode());
            requerir(item.precio() != null && item.precio().signum() >= 0, "Precio invalido: " + item.barcode());
            requerir(item.cantidad() > 0, "Cantidad invalida: " + item.barcode());
            porCodigo.merge(item.barcode(), item, (a, b) -> new SyncRequest.IngresoItem(
                    b.barcode(), b.format(), b.nombre(), b.precio(), a.cantidad() + b.cantidad()));
        }

        jdbc.sql("""
                        INSERT INTO rb_ingresos (id, local_id, device_id, user_email, created_at)
                        VALUES (:id, :localId, :deviceId, :email, :createdAt)
                        """)
                .param("id", id)
                .param("localId", p.localId())
                .param("deviceId", deviceId)
                .param("email", user.email())
                .param("createdAt", ts(p.createdAt()))
                .update();

        for (SyncRequest.IngresoItem item : porCodigo.values()) {
            UUID productId = upsertProducto(item.barcode(), item.format(), item.nombre(), item.precio(),
                    p.createdAt());
            jdbc.sql("INSERT INTO rb_ingreso_items (ingreso_id, product_id, cantidad) VALUES (:id, :pid, :cant)")
                    .param("id", id)
                    .param("pid", productId)
                    .param("cant", item.cantidad())
                    .update();
            sumarStock(p.localId(), productId, item.cantidad());
        }
        return SyncResponse.OK;
    }

    private String venta(AuthUser user, UUID deviceId, UUID id, SyncRequest.Venta p) {
        if (exists("rb_ventas", id)) {
            return SyncResponse.DUPLICADA;
        }
        exigirLocal(user, p.localId());
        requerir(p.createdAt() != null, "Falta la fecha de la venta");
        requerir(p.numero() != null && !p.numero().isBlank(), "Falta el numero de venta");
        requerir(p.formaPago() != null && FORMAS_DE_PAGO.contains(p.formaPago()),
                "Forma de pago invalida: " + p.formaPago());
        requerir(p.total() != null, "Falta el total");
        requerir(p.items() != null && !p.items().isEmpty(), "La venta no tiene productos");
        boolean numeroUsado = jdbc.sql("SELECT EXISTS (SELECT 1 FROM rb_ventas WHERE numero = :numero)")
                .param("numero", p.numero())
                .query(Boolean.class)
                .single();
        requerir(!numeroUsado, "Numero de venta repetido: " + p.numero());

        jdbc.sql("""
                        INSERT INTO rb_ventas (id, local_id, device_id, numero, user_email, forma_pago, total, created_at)
                        VALUES (:id, :localId, :deviceId, :numero, :email, :formaPago, :total, :createdAt)
                        """)
                .param("id", id)
                .param("localId", p.localId())
                .param("deviceId", deviceId)
                .param("numero", p.numero())
                .param("email", user.email())
                .param("formaPago", p.formaPago())
                .param("total", p.total())
                .param("createdAt", ts(p.createdAt()))
                .update();

        int linea = 0;
        for (SyncRequest.VentaItem item : p.items()) {
            requerir(item.barcode() != null && !item.barcode().isBlank(), "Producto sin codigo");
            requerir(item.cantidad() > 0, "Cantidad invalida: " + item.barcode());
            requerir(item.precio() != null, "Falta el precio: " + item.barcode());
            String nombre = item.nombre() == null || item.nombre().isBlank() ? item.barcode() : item.nombre();
            // La venta ya se hizo y se imprimio: si el producto no estuviera en el catalogo
            // se crea en vez de rechazarla, sin pisar uno existente.
            UUID productId = productoParaVenta(item.barcode(), nombre, item.precio());
            jdbc.sql("""
                            INSERT INTO rb_venta_items (venta_id, linea, product_id, barcode, nombre, precio_unit, cantidad)
                            VALUES (:id, :linea, :pid, :barcode, :nombre, :precio, :cant)
                            """)
                    .param("id", id)
                    .param("linea", ++linea)
                    .param("pid", productId)
                    .param("barcode", item.barcode())
                    .param("nombre", nombre)
                    .param("precio", item.precio())
                    .param("cant", item.cantidad())
                    .update();
            sumarStock(p.localId(), productId, -item.cantidad());
        }
        return SyncResponse.OK;
    }

    private String anulacion(AuthUser user, UUID deviceId, UUID id, SyncRequest.Anulacion p) {
        if (exists("rb_anulaciones", id)) {
            return SyncResponse.DUPLICADA;
        }
        exigirLocal(user, p.localId());
        requerir(p.createdAt() != null, "Falta la fecha de la anulacion");
        requerir(p.motivo() != null && !p.motivo().isBlank(), "Falta el motivo");
        requerir(p.ventaId() != null, "Falta la venta");

        // FOR UPDATE: dos anulaciones de la misma venta desde dos celulares no pasan las dos.
        VentaEstado venta = jdbc.sql("SELECT local_id, anulada FROM rb_ventas WHERE id = :id FOR UPDATE")
                .param("id", p.ventaId())
                .query(VentaEstado.class)
                .optional()
                .orElseThrow(() -> new Rechazo("La venta no existe"));
        requerir(p.localId().equals(venta.localId()), "La venta es de otro local");
        requerir(!venta.anulada(), "La venta ya estaba anulada");

        jdbc.sql("""
                        INSERT INTO rb_anulaciones (id, venta_id, device_id, user_email, motivo, created_at)
                        VALUES (:id, :ventaId, :deviceId, :email, :motivo, :createdAt)
                        """)
                .param("id", id)
                .param("ventaId", p.ventaId())
                .param("deviceId", deviceId)
                .param("email", user.email())
                .param("motivo", p.motivo())
                .param("createdAt", ts(p.createdAt()))
                .update();
        jdbc.sql("UPDATE rb_ventas SET anulada = TRUE WHERE id = :id").param("id", p.ventaId()).update();

        // Lo vendido vuelve al stock del local.
        jdbc.sql("""
                        INSERT INTO rb_stock (local_id, product_id, cantidad)
                        SELECT :localId, product_id, SUM(cantidad) FROM rb_venta_items
                        WHERE venta_id = :ventaId GROUP BY product_id
                        ON CONFLICT (local_id, product_id) DO UPDATE
                            SET cantidad = rb_stock.cantidad + EXCLUDED.cantidad, updated_at = now()
                        """)
                .param("localId", p.localId())
                .param("ventaId", p.ventaId())
                .update();
        return SyncResponse.OK;
    }

    private String cierre(AuthUser user, UUID deviceId, UUID id, SyncRequest.Cierre p) {
        if (exists("rb_cierres", id)) {
            return SyncResponse.DUPLICADA;
        }
        exigirLocal(user, p.localId());
        requerir(p.hasta() != null, "Falta la fecha del cierre");
        requerir(p.totalVentas() != null && p.totalAnulado() != null && p.neto() != null, "Faltan totales");

        String porFormaPago;
        try {
            porFormaPago = objectMapper.writeValueAsString(p.porFormaPago() == null ? Map.of() : p.porFormaPago());
        } catch (JsonProcessingException e) {
            throw new Rechazo("Totales por forma de pago invalidos");
        }
        jdbc.sql("""
                        INSERT INTO rb_cierres (id, local_id, device_id, user_email, desde, hasta, cantidad_ventas,
                                                total_ventas, cantidad_anuladas, total_anulado, neto, por_forma_pago)
                        VALUES (:id, :localId, :deviceId, :email, :desde, :hasta, :cantVentas,
                                :totalVentas, :cantAnuladas, :totalAnulado, :neto, CAST(:porFormaPago AS jsonb))
                        """)
                .param("id", id)
                .param("localId", p.localId())
                .param("deviceId", deviceId)
                .param("email", user.email())
                .param("desde", p.desde() == null ? null : ts(p.desde()))
                .param("hasta", ts(p.hasta()))
                .param("cantVentas", p.cantidadVentas())
                .param("totalVentas", p.totalVentas())
                .param("cantAnuladas", p.cantidadAnuladas())
                .param("totalAnulado", p.totalAnulado())
                .param("neto", p.neto())
                .param("porFormaPago", porFormaPago)
                .update();
        return SyncResponse.OK;
    }

    /**
     * Crea el producto o actualiza nombre/precio, salvo que el dato guardado sea mas nuevo
     * (un ingreso hecho sin conexion que llega tarde no pisa un precio posterior).
     */
    private UUID upsertProducto(String barcode, String format, String nombre, BigDecimal precio, Instant at) {
        jdbc.sql("""
                        INSERT INTO rb_products (barcode, format, nombre, precio, precio_at)
                        VALUES (:barcode, :format, :nombre, :precio, :at)
                        ON CONFLICT (barcode) DO UPDATE
                            SET nombre = EXCLUDED.nombre,
                                precio = EXCLUDED.precio,
                                format = COALESCE(EXCLUDED.format, rb_products.format),
                                precio_at = EXCLUDED.precio_at,
                                updated_at = now()
                            WHERE rb_products.precio_at <= EXCLUDED.precio_at
                        """)
                .param("barcode", barcode)
                .param("format", format)
                .param("nombre", nombre)
                .param("precio", precio)
                .param("at", ts(at))
                .update();
        return productId(barcode);
    }

    private UUID productoParaVenta(String barcode, String nombre, BigDecimal precio) {
        jdbc.sql("""
                        INSERT INTO rb_products (barcode, nombre, precio, precio_at)
                        VALUES (:barcode, :nombre, :precio, :at)
                        ON CONFLICT (barcode) DO NOTHING
                        """)
                .param("barcode", barcode)
                .param("nombre", nombre)
                .param("precio", precio)
                .param("at", ts(Instant.EPOCH))
                .update();
        return productId(barcode);
    }

    private UUID productId(String barcode) {
        return jdbc.sql("SELECT id FROM rb_products WHERE barcode = :barcode")
                .param("barcode", barcode)
                .query(UUID.class)
                .single();
    }

    private void sumarStock(UUID localId, UUID productId, int cantidad) {
        jdbc.sql("""
                        INSERT INTO rb_stock (local_id, product_id, cantidad) VALUES (:localId, :pid, :cant)
                        ON CONFLICT (local_id, product_id) DO UPDATE
                            SET cantidad = rb_stock.cantidad + EXCLUDED.cantidad, updated_at = now()
                        """)
                .param("localId", localId)
                .param("pid", productId)
                .param("cant", cantidad)
                .update();
    }

    private boolean exists(String table, UUID id) {
        // table es siempre una constante de esta clase, nunca un dato del request.
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE id = :id)")
                .param("id", id)
                .query(Boolean.class)
                .single();
    }

    private void exigirLocal(AuthUser user, UUID localId) {
        requerir(localId != null, "Falta el local");
        requerir(accessService.puedeOperar(user, localId), "Sin acceso al local");
    }

    private <T> T payload(SyncRequest.Op op, Class<T> type) {
        try {
            return objectMapper.treeToValue(op.payload(), type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new Rechazo("Datos invalidos: " + e.getMessage());
        }
    }

    private static void requerir(boolean condicion, String motivo) {
        if (!condicion) {
            throw new Rechazo(motivo);
        }
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private static SyncResponse.Result result(SyncRequest.Op op, String status, String message) {
        return new SyncResponse.Result(op.opId(), status, message);
    }

    record VentaEstado(UUID localId, boolean anulada) {
    }

    /** Operacion que nunca se va a poder registrar; la transaccion se deshace. */
    static class Rechazo extends RuntimeException {
        Rechazo(String motivo) {
            super(motivo);
        }
    }
}
