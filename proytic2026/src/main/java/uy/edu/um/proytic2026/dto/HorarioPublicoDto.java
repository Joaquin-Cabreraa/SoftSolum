package uy.edu.um.proytic2026.dto;

import java.time.LocalDate;
import java.time.LocalTime;

// Un horario concreto de una actividad, tal como lo muestra la API publica.
public record HorarioPublicoDto(
        LocalDate fecha,
        LocalTime horaInicio,
        LocalTime horaFin,
        Integer cuposDisponibles) {
}
