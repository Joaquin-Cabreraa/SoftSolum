package uy.edu.um.proytic2026.dto;

import java.math.BigDecimal;
import java.util.List;

// Lo que la API publica muestra de una actividad.
// "horarios" son los horarios que corresponden a la consulta (por ejemplo, los de un dia).
// "promedio" es null si todavia no tiene valoraciones.
public record ActividadPublicaDto(
        Long id,
        String titulo,
        String descripcion,
        String departamento,
        String localidad,
        BigDecimal precio,
        Long emprendimientoId,
        String emprendimiento,
        Double promedio,
        Long cantidadValoraciones,
        List<HorarioPublicoDto> horarios) {
}
