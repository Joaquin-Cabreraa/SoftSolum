package uy.edu.um.proytic2026.pagos;

import uy.edu.um.proytic2026.entities.EstadoPago;

// Lo que contesta la pasarela
public record ResultadoPago(
        EstadoPago estado,
        String idExterno,        // id del pago en la pasarela
        String detalle,          // motivo (ej. "cc_rejected_insufficient_amount")
        String medioPago,
        String ultimosDigitos) {
}
