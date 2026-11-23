
package uy.edu.um.proytic2026.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReservaCanceladaEvent(
        String emailTurista,
        String nombreActividad,
        LocalDate fecha,
        LocalTime hora,
        boolean canceladaPorPrestador,
        boolean estabaPaga,
        BigDecimal montoReembolsado,
        String motivoCancelacion
) {
}
