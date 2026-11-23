package uy.edu.um.proytic2026.services;

import java.time.LocalDate;
import java.time.LocalTime;

public record ListaEsperaRegistradaEvent(
        String emailTurista,
        String nombreActividad,
        LocalDate fecha,
        LocalTime hora,
        int cantidad
) {
}
