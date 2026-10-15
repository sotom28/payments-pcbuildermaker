package com.example.payments.auth;

import com.example.payments.exception.ForbiddenException;
import com.example.payments.exception.UnauthorizedException;

/**
 * Identidad de la request, tomada de los headers que inyecta el BFF tras validar el JWT de Entra ID
 * (mismo contrato que reports-service y ms-user). Este servicio no debe exponerse directo a internet:
 * solo el BFF puede llegar a el.
 */
public record AuthContext(String userId, String role) {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String ROLE_HEADER = "X-User-Role";
    public static final String ROLE_WORKSHOP_ADMIN = "WORKSHOP_ADMIN";

    public AuthContext {
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("Falta el encabezado de identidad: " + USER_ID_HEADER);
        }
    }

    public boolean isAdmin() {
        return ROLE_WORKSHOP_ADMIN.equals(role);
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new ForbiddenException("Solo el admin del taller (WORKSHOP_ADMIN) puede hacer esto");
        }
    }
}
