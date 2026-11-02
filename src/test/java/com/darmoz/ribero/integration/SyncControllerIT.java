package com.darmoz.ribero.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flujo completo de un vendedor: el administrador crea el local y se lo asigna, el vendedor
 * registra el celular y sincroniza ingreso, venta, anulacion y cierre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SyncControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private String adminToken;
    private String vendedorToken;
    private String vendedorEmail;
    private UUID localId;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        adminToken = TestJwtFactory.issueToken(UUID.randomUUID(), "admin@ribero.com", Set.of("APP_ADMIN"));
        vendedorEmail = "vendedor-" + UUID.randomUUID() + "@ribero.com";
        vendedorToken = TestJwtFactory.issueToken(UUID.randomUUID(), vendedorEmail, Set.of("VENDEDOR"));

        localId = crearLocal("Local " + UUID.randomUUID());
        asignar(vendedorEmail, List.of(localId));

        deviceId = UUID.randomUUID();
        ResponseEntity<JsonNode> device = post("/api/rb/devices", vendedorToken,
                Map.of("id", deviceId, "nombre", "Celular de prueba"));
        assertThat(device.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(device.getBody().get("code").asText()).startsWith("C");
    }

    @Test
    void vendedorVeSoloSusLocales() {
        UUID otroLocal = crearLocal("Otro " + UUID.randomUUID());

        JsonNode me = get("/api/rb/me", vendedorToken).getBody();
        assertThat(me.get("admin").asBoolean()).isFalse();
        assertThat(me.get("locales")).hasSize(1);
        assertThat(me.get("locales").get(0).get("id").asText()).isEqualTo(localId.toString());

        JsonNode meAdmin = get("/api/rb/me", adminToken).getBody();
        assertThat(meAdmin.get("admin").asBoolean()).isTrue();
        assertThat(meAdmin.get("locales").findValuesAsText("id")).contains(localId.toString(), otroLocal.toString());
    }

    @Test
    void vendedorNoPuedeAdministrar() {
        ResponseEntity<JsonNode> response = post("/api/rb/admin/locales", vendedorToken, Map.of("nombre", "X"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void ingresoVentaAnulacionYCierreMuevenElStock() {
        String barcode = "779" + System.nanoTime();
        UUID ingresoId = UUID.randomUUID();
        Map<String, Object> ingreso = op(ingresoId, "INGRESO", Map.of(
                "localId", localId, "createdAt", Instant.now().toString(),
                "items", List.of(item(barcode, "Yerba", "120.00", 5))));

        assertThat(statuses(sync(ingreso))).containsExactly("ok");
        // Reenviar la misma operacion (el celular no recibio la respuesta) no la duplica.
        assertThat(statuses(sync(ingreso))).containsExactly("duplicada");
        assertThat(stock(barcode)).isEqualTo(5);

        UUID ventaId = UUID.randomUUID();
        Map<String, Object> venta = op(ventaId, "VENTA", Map.of(
                "localId", localId, "numero", "T-" + UUID.randomUUID().toString().substring(0, 8),
                "formaPago", "EFECTIVO", "total", "240.00", "createdAt", Instant.now().toString(),
                "items", List.of(Map.of("barcode", barcode, "nombre", "Yerba", "precio", "120.00", "cantidad", 2))));
        Map<String, Object> anulacion = op(UUID.randomUUID(), "ANULACION", Map.of(
                "localId", localId, "ventaId", ventaId, "motivo", "Error de cobro",
                "createdAt", Instant.now().toString()));
        Map<String, Object> anulacionRepetida = op(UUID.randomUUID(), "ANULACION", Map.of(
                "localId", localId, "ventaId", ventaId, "motivo", "Otra vez",
                "createdAt", Instant.now().toString()));
        Map<String, Object> cierre = op(UUID.randomUUID(), "CIERRE", Map.of(
                "localId", localId, "hasta", Instant.now().toString(),
                "cantidadVentas", 1, "totalVentas", "240.00", "cantidadAnuladas", 1, "totalAnulado", "240.00",
                "neto", "0.00", "porFormaPago", Map.of("EFECTIVO", "0.00")));

        JsonNode result = sync(venta, anulacion, anulacionRepetida, cierre);
        assertThat(statuses(result)).containsExactly("ok", "ok", "rechazada", "ok");
        assertThat(result.get("results").get(2).get("message").asText()).contains("ya estaba anulada");
        // Vendio 2 y la anulacion los devolvio.
        assertThat(stock(barcode)).isEqualTo(5);
    }

    @Test
    void ingresoAtrasadoNoPisaUnPrecioMasNuevo() {
        String barcode = "780" + System.nanoTime();
        Instant ahora = Instant.now();
        sync(op(UUID.randomUUID(), "INGRESO", Map.of("localId", localId, "createdAt", ahora.toString(),
                "items", List.of(item(barcode, "Nuevo", "200.00", 1)))));
        sync(op(UUID.randomUUID(), "INGRESO", Map.of("localId", localId,
                "createdAt", ahora.minusSeconds(3600).toString(),
                "items", List.of(item(barcode, "Viejo", "100.00", 1)))));

        JsonNode producto = producto(barcode);
        assertThat(producto.get("nombre").asText()).isEqualTo("Nuevo");
        assertThat(producto.get("precio").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(producto.get("stock").asInt()).isEqualTo(2);
    }

    @Test
    void operacionEnLocalSinAccesoSeRechaza() {
        UUID ajeno = crearLocal("Ajeno " + UUID.randomUUID());
        JsonNode result = sync(op(UUID.randomUUID(), "INGRESO", Map.of("localId", ajeno,
                "createdAt", Instant.now().toString(), "items", List.of(item("123", "X", "1.00", 1)))));
        assertThat(statuses(result)).containsExactly("rechazada");

        ResponseEntity<JsonNode> catalog = get("/api/rb/locales/" + ajeno + "/catalog", vendedorToken);
        assertThat(catalog.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void celularNoRegistradoSeRechaza() {
        Map<String, Object> body = Map.of("deviceId", UUID.randomUUID(), "ops", List.of(
                op(UUID.randomUUID(), "CIERRE", Map.of("localId", localId))));
        JsonNode result = post("/api/rb/sync", vendedorToken, body).getBody();
        assertThat(statuses(result)).containsExactly("rechazada");
    }

    // ---- helpers ----

    private UUID crearLocal(String nombre) {
        ResponseEntity<JsonNode> response = post("/api/rb/admin/locales", adminToken,
                Map.of("nombre", nombre, "direccion", "Calle 123"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(response.getBody().get("id").asText());
    }

    private void asignar(String email, List<UUID> localIds) {
        ResponseEntity<JsonNode> response = exchange(HttpMethod.PUT, "/api/rb/admin/asignaciones", adminToken,
                Map.of("email", email, "localIds", localIds));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private JsonNode sync(Map<?, ?>... ops) {
        ResponseEntity<JsonNode> response = post("/api/rb/sync", vendedorToken,
                Map.of("deviceId", deviceId, "ops", List.of(ops)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private JsonNode producto(String barcode) {
        JsonNode catalog = get("/api/rb/locales/" + localId + "/catalog", vendedorToken).getBody();
        for (JsonNode p : catalog.get("products")) {
            if (p.get("barcode").asText().equals(barcode)) {
                return p;
            }
        }
        throw new AssertionError("Producto no encontrado en el catalogo: " + barcode);
    }

    private int stock(String barcode) {
        return producto(barcode).get("stock").asInt();
    }

    private static Map<String, Object> op(UUID opId, String type, Map<String, Object> payload) {
        return Map.of("opId", opId, "type", type, "payload", payload);
    }

    private static Map<String, Object> item(String barcode, String nombre, String precio, int cantidad) {
        return Map.of("barcode", barcode, "format", "EAN13", "nombre", nombre, "precio", precio,
                "cantidad", cantidad);
    }

    private static List<String> statuses(JsonNode syncResponse) {
        return syncResponse.get("results").findValuesAsText("status");
    }

    private ResponseEntity<JsonNode> get(String url, String token) {
        return exchange(HttpMethod.GET, url, token, null);
    }

    private ResponseEntity<JsonNode> post(String url, String token, Object body) {
        return exchange(HttpMethod.POST, url, token, body);
    }

    private ResponseEntity<JsonNode> exchange(HttpMethod method, String url, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
