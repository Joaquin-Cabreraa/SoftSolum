package uy.edu.um.proytic2026.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Reseña de un turista sobre una actividad que ya hizo.
// Una por reserva (Reserva 1 --- 0..1 Resena) y solo cuando el horario ya terminó.
// El prestador puede responder (no borrar). El administrador puede ocultarla si es inapropiada.
@Entity
@Table(name = "Resenas")
@Getter
@Setter
@NoArgsConstructor
public class Resena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "reserva_id", nullable = false, unique = true)
    private Reserva reserva;

    // Se guarda también la actividad para calcular el promedio rápido
    @ManyToOne(optional = false)
    @JoinColumn(name = "actividad_id", nullable = false)
    private Actividad actividad;

    @ManyToOne(optional = false)
    @JoinColumn(name = "turista_id", nullable = false)
    private Turista turista;

    @Column(nullable = false)
    private Integer puntaje;            // 1 a 5 estrellas

    @Column(length = 1000)
    private String comentario;

    @Column(nullable = false)
    private LocalDateTime fecha;

    // Respuesta del prestador (una sola, la puede corregir; no puede borrar la reseña)
    @Column(name = "respuesta_prestador", length = 1000)
    private String respuestaPrestador;

    @Column(name = "fecha_respuesta")
    private LocalDateTime fechaRespuesta;

    // Moderación del administrador
    @Column(name = "oculta")
    private Boolean oculta;

    @Column(name = "motivo_oculta", length = 300)
    private String motivoOculta;

    public boolean estaOculta() {
        return Boolean.TRUE.equals(oculta);
    }

    // "Ana P." (así se muestra en el portal, sin el apellido completo)
    public String autor() {
        String nombre = turista.getName() == null ? "Turista" : turista.getName();
        String apellido = turista.getLastName();
        return apellido == null || apellido.isBlank() ? nombre : nombre + " " + apellido.charAt(0) + ".";
    }
}
