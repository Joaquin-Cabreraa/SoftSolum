package uy.edu.um.proytic2026.entities;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class HorarioId implements Serializable {

    private Long actividad;
    private LocalDate fecha;
    private LocalTime horaInicio;
}{
}
