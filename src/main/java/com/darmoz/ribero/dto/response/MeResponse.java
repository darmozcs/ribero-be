package com.darmoz.ribero.dto.response;

import java.util.List;
import java.util.Set;

/** Datos que la app necesita despues del login: quien es y a que locales puede entrar. */
public record MeResponse(String email, Set<String> roles, boolean admin, List<LocalResponse> locales) {
}
