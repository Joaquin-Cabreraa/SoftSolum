package uy.edu.um.proytic2026.entities;

// Resultado de un intento de pago
public enum EstadoPago {
    APROBADO,     // la pasarela aprobó el cobro -> la reserva queda CONFIRMADA
    RECHAZADO,    // la tarjeta fue rechazada -> la reserva sigue pendiente y se puede reintentar
    PENDIENTE,    // la pasarela lo dejó en revisión
    ERROR         // no se pudo hablar con la pasarela (caída, sin respuesta)
}
