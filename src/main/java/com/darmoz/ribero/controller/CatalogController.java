package com.darmoz.ribero.controller;

import com.darmoz.ribero.exception.NotFoundException;
import com.darmoz.ribero.exception.ValidationException;
import com.darmoz.ribero.security.AuthUser;
import com.darmoz.ribero.service.AccessService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Catalogo para trabajar sin conexion: el celular baja productos, precios y stock del local
 * y despues pide solo lo que cambio desde la ultima vez (since = serverTime anterior).
 */
@RestController
@RequestMapping("/api/rb")
public class CatalogController {

    private static final int MAX_PHOTO_BYTES = 2 * 1024 * 1024;

    private final JdbcClient jdbc;
    private final AccessService accessService;

    public CatalogController(JdbcClient jdbc, AccessService accessService) {
        this.jdbc = jdbc;
        this.accessService = accessService;
    }

    @GetMapping("/locales/{localId}/catalog")
    public CatalogResponse catalog(@AuthenticationPrincipal AuthUser user, @PathVariable UUID localId,
                                   @RequestParam(required = false) Instant since) {
        accessService.exigirAcceso(user, localId);
        // Un minuto hacia atras: una transaccion que empezo antes de esta consulta y commitea
        // despues tiene updated_at anterior a "ahora"; con el margen igual entra en el
        // proximo pedido (repetir un producto no molesta, perderlo si).
        Instant serverTime = Instant.now().minus(1, ChronoUnit.MINUTES);
        List<CatalogItem> items = jdbc.sql("""
                        SELECT p.barcode, p.format, p.nombre, p.precio, p.has_photo,
                               COALESCE(s.cantidad, 0) AS stock, COALESCE(s.activo, TRUE) AS activo,
                               COALESCE(s.eliminado, FALSE) AS eliminado
                        FROM rb_products p
                        LEFT JOIN rb_stock s ON s.product_id = p.id AND s.local_id = :localId
                        WHERE p.updated_at > :since OR s.updated_at > :since
                        ORDER BY p.nombre
                        """)
                .param("localId", localId)
                .param("since", java.sql.Timestamp.from(since == null ? Instant.EPOCH : since))
                .query(CatalogItem.class)
                .list();
        return new CatalogResponse(serverTime, items);
    }

    @GetMapping(value = "/products/{barcode}/photo", produces = MediaType.IMAGE_JPEG_VALUE)
    public byte[] photo(@PathVariable String barcode) {
        return jdbc.sql("""
                        SELECT ph.jpeg FROM rb_product_photos ph
                        JOIN rb_products p ON p.id = ph.product_id
                        WHERE p.barcode = :barcode
                        """)
                .param("barcode", barcode)
                .query(byte[].class)
                .optional()
                .orElseThrow(() -> new NotFoundException("El producto no tiene foto"));
    }

    /**
     * La foto se sube despues de sincronizar el ingreso que creo el producto; si el producto
     * todavia no existe responde 404 y el celular reintenta en la proxima sincronizacion.
     */
    @PutMapping(value = "/products/{barcode}/photo", consumes = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<Void> uploadPhoto(@PathVariable String barcode, @RequestBody byte[] jpeg) {
        if (jpeg.length == 0 || jpeg.length > MAX_PHOTO_BYTES) {
            throw new ValidationException("La foto tiene que pesar entre 1 byte y 2 MB");
        }
        UUID productId = jdbc.sql("SELECT id FROM rb_products WHERE barcode = :barcode")
                .param("barcode", barcode)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new NotFoundException("Producto inexistente: " + barcode));
        jdbc.sql("""
                        INSERT INTO rb_product_photos (product_id, jpeg) VALUES (:id, :jpeg)
                        ON CONFLICT (product_id) DO UPDATE SET jpeg = EXCLUDED.jpeg, updated_at = now()
                        """)
                .param("id", productId)
                .param("jpeg", jpeg)
                .update();
        jdbc.sql("UPDATE rb_products SET has_photo = TRUE, updated_at = now() WHERE id = :id")
                .param("id", productId)
                .update();
        return ResponseEntity.noContent().build();
    }

    /**
     * activo = false: inhabilitado en este local (no se vende). eliminado = true: se saco del
     * catalogo de este local; viaja igual para que el celular lo borre de su copia.
     */
    public record CatalogItem(String barcode, String format, String nombre, BigDecimal precio,
                              boolean hasPhoto, int stock, boolean activo, boolean eliminado) {
    }

    public record CatalogResponse(Instant serverTime, List<CatalogItem> products) {
    }
}
