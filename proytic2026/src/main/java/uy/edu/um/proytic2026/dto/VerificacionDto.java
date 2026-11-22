package uy.edu.um.proytic2026.dto;

// Respuesta de "verificar emprendimiento".
// existe: hay un emprendimiento con ese id. verificado: ademas fue ACEPTADO por la administracion.
// nombre: solo se muestra si esta verificado (los pendientes o denegados no son publicos).
public record VerificacionDto(
        Long id,
        boolean existe,
        boolean verificado,
        String nombre) {
}
