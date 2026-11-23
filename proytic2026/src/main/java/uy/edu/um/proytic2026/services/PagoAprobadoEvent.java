package uy.edu.um.proytic2026.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record PagoAprobadoEvent(
        String emailTurista,
        String nombreActividad,
        LocalDate fecha,
        LocalTime hora,
        int cantidad,
        BigDecimal monto
) {
}
