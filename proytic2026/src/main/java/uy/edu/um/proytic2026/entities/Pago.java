package uy.edu.um.proytic2026.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Cada intento de pago de una reserva (aprobado o no).
// Relación "se paga con": Reserva (1) --- (n) Pago. Una reserva puede tener varios
// intentos rechazados y, como mucho, uno aprobado.
// Nunca se guardan los datos de la tarjeta: solo los últimos 4 números y la marca.
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

    // Lo que se intentó cobrar (siempre el monto de la reserva, nunca lo que mande el navegador)
    @Column(nullable = false)
    private BigDecimal monto;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPago estado;

    // Id del pago en Mercado Pago (para reembolsos y para rastrearlo)
    @Column(name = "id_externo")
    private String idExterno;

    // "visa", "master"...
    @Column(name = "medio_pago")
    private String medioPago;

    @Column(name = "ultimos_digitos", length = 4)
    private String ultimosDigitos;

    // Motivo que devolvió la pasarela (ej. "cc_rejected_insufficient_amount")
    @Column(length = 500)
    private String detalle;

    // Si después se canceló la reserva y se devolvió plata
    @Column(name = "monto_reembolsado")
    private BigDecimal montoReembolsado;
}
