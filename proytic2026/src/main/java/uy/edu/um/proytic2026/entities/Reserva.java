package uy.edu.um.proytic2026.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Reserva que hace un turista sobre un horario concreto de una actividad.
@Entity
@Table(name = "Reservas")
@Getter
@Setter
@NoArgsConstructor
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación "realiza": Turista (1) --- (n) Reserva
    @ManyToOne(optional = false)
    @JoinColumn(name = "turista_id", nullable = false)
    private Turista turista;

    // Relación "sobre": Horario (1) --- (n) Reserva.
    // Horario es entidad débil con clave compuesta (actividad + fecha + hora de inicio),
    // por eso la clave foránea tiene 3 columnas.
    @ManyToOne(optional = false)
    @JoinColumns({
            @JoinColumn(name = "horario_actividad_id", referencedColumnName = "actividad_id", nullable = false),
            @JoinColumn(name = "horario_fecha", referencedColumnName = "fecha", nullable = false),
            @JoinColumn(name = "horario_hora_inicio", referencedColumnName = "hora_inicio", nullable = false)
    })
    private Horario horario;

    @Column(name = "cant_cupos", nullable = false)
    private Integer cantCupos;

    // Nota libre del turista (ej. "voy en silla de ruedas")
    @Column(length = 500)
    private String observacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoReserva estado = EstadoReserva.PENDIENTE_PAGO;

    // Se usa para el plazo de 2 horas para pagar (RNE #2)
    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    // Precio de la actividad x cantidad de cupos (RNE #11).
    // Se guarda para que no cambie si después cambia el precio de la actividad.
    @Column(name = "monto_total", nullable = false)
    private BigDecimal montoTotal;

    // Relación identificadora "incluye": Reserva (1) --- (n) Participante
    @OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Participante> participantes = new ArrayList<>();

    public void agregarParticipante(Participante participante) {
        participante.setReserva(this);
        participantes.add(participante);
    }
}
