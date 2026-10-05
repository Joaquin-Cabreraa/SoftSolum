package uy.edu.um.proytic2026.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "Horarios")
@Getter
@Setter
@NoArgsConstructor
@IdClass(HorarioId.class)
public class Horario {

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "actividad_id", nullable = false)
    private Actividad actividad;

    @Id
    @Column(nullable = false)
    private LocalDate fecha;

    @Id
    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "cupos_totales", nullable = false)
    private Integer cuposTotales;

    @Column(name = "cupos_disponibles", nullable = false)
    private Integer cuposDisponibles;

    @Column(nullable = false)
    private Boolean cancelable;

    @Column(name = "porcentaje_reembolso")
    private Integer porcentajeReembolso;

    @Column(name = "min_minutos_cancelacion")
    private Integer minMinutosCancelacion;
}