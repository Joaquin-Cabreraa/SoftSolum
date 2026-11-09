package uy.edu.um.proytic2026.entities;

// Estados posibles de una reserva (ver casos de uso CU-T06 a CU-T09 y RNE #1, #2)
public enum EstadoReserva {
    PENDIENTE_PAGO,   // recién creada: el cupo ya está retenido, falta pagar (máximo 2 horas)
    CONFIRMADA,       // el pago fue aprobado
    CANCELADA         // venció el plazo de pago (o se canceló)
}
