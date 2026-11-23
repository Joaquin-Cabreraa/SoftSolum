package uy.edu.um.proytic2026.services;

import java.nio.charset.StandardCharsets;

// Reglas de seguridad para las contraseñas nuevas.
// Las contraseñas existentes no se modifican.
public final class PoliticaContrasenas {

    private PoliticaContrasenas() {
    }

    // Devuelve un mensaje de error o null si la contraseña es válida.
    public static String validar(String contrasena) {

        if (contrasena == null || contrasena.length() < 15) {
            return "La contraseña debe tener al menos 15 caracteres.";
        }

        // BCrypt admite como máximo 72 bytes de contraseña.
        if (contrasena.getBytes(StandardCharsets.UTF_8).length > 72) {
            return "La contraseña es demasiado larga. Probá con una más corta.";
        }

        return null;
    }
}