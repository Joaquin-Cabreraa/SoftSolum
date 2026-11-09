package uy.edu.um.proytic2026.entities;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// Clave compuesta de Participante (entidad débil): reserva + CI.
// Mismo mecanismo que HorarioId.
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ParticipanteId implements Serializable {
    private Long reserva;
    private String ci;
}
