package uy.edu.um.proytic2026.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Un intento de pago (o una devolución) de una reserva.
// Relación "se paga con": Reserva (1) --- (n) Pago. Cada intento queda guardado (RNE #14),
// también los rechazados y los que fallaron por error o timeout.
// Por seguridad NUNCA se guarda el número completo de la tarjeta ni el CVV: solo los últimos 4 dígitos.
@Entity
@Table(name = "Pagos")
@Getter
@Setter
@NoArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reserva reserva;

    // Siempre positivo. Si es un REEMBOLSO, es lo que se le devuelve al turista.
    @Column(nullable = false)
    private BigDecimal monto;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPago estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoPago tipo = TipoPago.COBRO;

    @Column(name = "ultimos4", length = 4)
    private String ultimos4;

    // Código que devolvió la pasarela (null si no hubo respuesta, ej. timeout)
    @Column(name = "codigo_pasarela", length = 40)
    private String codigoPasarela;

    // Motivo en texto: "Fondos insuficientes", "La pasarela no respondió a tiempo", etc.
    @Column(length = 200)
    private String detalle;
}
