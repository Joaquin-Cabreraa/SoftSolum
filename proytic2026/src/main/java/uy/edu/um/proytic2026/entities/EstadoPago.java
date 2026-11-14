package uy.edu.um.proytic2026.entities;

// Resultado de un intento de pago (RNE #14: cada intento queda guardado)
public enum EstadoPago {
    APROBADO,     // la pasarela aceptó el pago
    RECHAZADO,    // la pasarela respondió, pero no aceptó la tarjeta (ej. fondos insuficientes)
    ERROR         // no hubo respuesta válida: pasarela caída, timeout, respuesta rara
}
