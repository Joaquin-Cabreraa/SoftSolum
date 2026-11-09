package uy.edu.um.proytic2026.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

// Cada persona del grupo de una reserva (acta del 31-08: se pide nombre, apellido,
// fecha de nacimiento y CI de cada uno). Entidad débil de Reserva: se identifica
// con la reserva + su CI.
@Entity
@Table(name = "Participantes")
@IdClass(ParticipanteId.class)
@Getter
@Setter
@NoArgsConstructor
public class Participante {

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reserva reserva;

    @Id
    @Column(nullable = false, length = 20)
    private String ci;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;
}
