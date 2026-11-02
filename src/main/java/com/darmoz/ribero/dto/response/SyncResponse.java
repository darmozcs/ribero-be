package com.darmoz.ribero.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Un resultado por operacion procesada, en el mismo orden:
 * <ul>
 *   <li>{@code ok}: se registro.</li>
 *   <li>{@code duplicada}: ya estaba registrada (un reenvio); el celular la da por enviada.</li>
 *   <li>{@code rechazada}: no se puede registrar nunca (motivo en message); el celular la
 *       saca de la cola y la muestra como error.</li>
 *   <li>{@code error}: fallo inesperado; el celular la reintenta. El procesamiento se corta
 *       ahi, asi que las operaciones siguientes no tienen resultado y quedan pendientes
 *       (una anulacion no puede adelantarse a la venta que anula).</li>
 * </ul>
 */
public record SyncResponse(List<Result> results) {

    public static final String OK = "ok";
    public static final String DUPLICADA = "duplicada";
    public static final String RECHAZADA = "rechazada";
    public static final String ERROR = "error";

    public record Result(UUID opId, String status, String message) {
    }
}
