package com.darmoz.ribero.dto.response;

import java.util.UUID;

public record LocalResponse(UUID id, String nombre, String direccion, boolean activo) {
}
