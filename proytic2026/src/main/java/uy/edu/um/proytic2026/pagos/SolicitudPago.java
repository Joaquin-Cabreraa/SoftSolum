package uy.edu.um.proytic2026.pagos;

import java.math.BigDecimal;

// Lo que se le manda a la pasarela para cobrar.
// El monto lo pone SIEMPRE el servidor (sale de la reserva), nunca el navegador.
// token: la tarjeta ya convertida en un código por el SDK de Mercado Pago en el navegador
//        (nuestro servidor nunca ve el número de la tarjeta).
public record SolicitudPago(
        BigDecimal monto,
        String descripcion,
        String referencia,          // ej. "reserva-15" (para encontrarlo en el panel de Mercado Pago)
        String claveIdempotencia,   // si el mismo pedido llega dos veces, se cobra una sola vez
        String token,
        String medioPagoId,         // "visa", "master"...
        String emisorId,
        Integer cuotas,
        String emailPagador,
        String tipoDocumento,
        String numeroDocumento,
        // Solo para la pasarela simulada (el navegador manda únicamente esto de la tarjeta)
        String titular,
        String ultimosDigitos) {
}
