package uy.edu.um.proytic2026.pagos;

import uy.edu.um.proytic2026.entities.EstadoPago;

import java.math.BigDecimal;
import java.util.UUID;

// Pasarela "de mentira" para cuando no hay credenciales de Mercado Pago configuradas
// (por ejemplo, en la compu de alguien del equipo que no las tiene).
// Imita las tarjetas de prueba de Mercado Pago: el resultado depende del NOMBRE DEL TITULAR.
//   APRO -> aprobado          OTHE / FUND / SECU / EXPI / FORM / CALL -> rechazado
//   CONT -> pendiente         ERROR -> simula que la pasarela no responde
//   cualquier otro nombre -> aprobado
public class PasarelaSimulada implements PasarelaPago {

    @Override
    public ResultadoPago cobrar(SolicitudPago s) {
        String titular = s.titular() == null ? "" : s.titular().trim().toUpperCase();
        String id = "SIM-" + UUID.randomUUID().toString().substring(0, 8);

        return switch (titular) {
            case "ERROR" -> throw new IllegalStateException("La pasarela no responde (simulado)");
            case "CONT" -> new ResultadoPago(EstadoPago.PENDIENTE, id, "pending_contingency", s.medioPagoId(), s.ultimosDigitos());
            case "OTHE" -> rechazo(id, "cc_rejected_other_reason", s);
            case "FUND" -> rechazo(id, "cc_rejected_insufficient_amount", s);
            case "SECU" -> rechazo(id, "cc_rejected_bad_filled_security_code", s);
            case "EXPI" -> rechazo(id, "cc_rejected_bad_filled_date", s);
            case "FORM" -> rechazo(id, "cc_rejected_bad_filled_other", s);
            case "CALL" -> rechazo(id, "cc_rejected_call_for_authorize", s);
            default -> new ResultadoPago(EstadoPago.APROBADO, id, "accredited", s.medioPagoId(), s.ultimosDigitos());
        };
    }

    private ResultadoPago rechazo(String id, String motivo, SolicitudPago s) {
        return new ResultadoPago(EstadoPago.RECHAZADO, id, motivo, s.medioPagoId(), s.ultimosDigitos());
    }

    @Override
    public boolean reembolsar(String idExterno, BigDecimal monto, String claveIdempotencia) {
        return true;
    }

    @Override
    public String nombre() {
        return "SIMULADA";
    }

    @Override
    public String clavePublica() {
        return null;
    }
}
