package uy.edu.um.proytic2026.dto;

// Lo que la API pública muestra de un emprendimiento.
// No incluye RUT, documento ni datos del prestador (no son públicos).
public record EmprendimientoPublicoDto(
        Long id,
        String nombre,
        String telefono,
        String email,
        String ubicacion,
        String descripcion) {
}
