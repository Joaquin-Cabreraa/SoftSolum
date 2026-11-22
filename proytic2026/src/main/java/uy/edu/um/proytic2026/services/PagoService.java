package uy.edu.um.proytic2026.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.pagos.PasarelaPago;
import uy.edu.um.proytic2026.pagos.ResultadoPago;
import uy.edu.um.proytic2026.pagos.SolicitudPago;
import uy.edu.um.proytic2026.repository.PagoRepository;
import uy.edu.um.proytic2026.repository.ReservaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Pago de reservas (CU-T07) con la pasarela configurada (Mercado Pago o la simulada).
// Reglas:
// - solo el dueño de la reserva la puede pagar, y solo si está PENDIENTE_PAGO y dentro de las 2 horas;
// - el monto sale de la reserva (nunca de lo que mande el navegador);
// - si el pago se aprueba, la reserva queda CONFIRMADA;
// - si se rechaza o la pasarela no responde, la reserva NO se confirma: sigue pendiente,
//   el cupo sigue guardado y el turista puede reintentar.
@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    @Autowired private PagoRepository pagoRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private PasarelaPago pasarela;

    // Lo que vuelve a la página después de intentar pagar
    public record RespuestaPago(EstadoPago estado, String mensaje) {
        public boolean aprobado() { return estado == EstadoPago.APROBADO; }
    }

    // ¿Esta reserva se puede pagar ahora? null = sí; si no, el motivo.
    public String validarPago(Long turistaId, Reserva reserva) {
        if (reserva == null || !reserva.getTurista().getId().equals(turistaId)) {
            return "La reserva no existe.";
        }
        if (reserva.getEstado() == EstadoReserva.CONFIRMADA) {
            return "Esta reserva ya está paga.";
        }
        if (reserva.getEstado() == EstadoReserva.EN_ESPERA) {
            return "Estás en lista de espera: vas a poder pagar cuando se libere un lugar.";
        }
        if (reserva.getEstado() != EstadoReserva.PENDIENTE_PAGO) {
            return "La reserva está cancelada.";
        }
        if (!LocalDateTime.now().isBefore(venceElPago(reserva))) {
            return "Venció el plazo de " + ReservaService.HORAS_PARA_PAGAR + " horas para pagar esta reserva.";
        }
        if (pagoRepository.findFirstByReservaIdAndEstado(reserva.getId(), EstadoPago.APROBADO).isPresent()) {
            return "Esta reserva ya está paga.";
        }
        return null;
    }

    // Intenta cobrar la reserva. "datos" trae lo que mandó el navegador (token, medio de pago, etc.);
    // el monto, la descripción y la referencia los arma este método.
    public RespuestaPago pagar(Long turistaId, Long reservaId, SolicitudPago datos) {
        Reserva reserva = reservaRepository.findById(reservaId).orElse(null);
        String error = validarPago(turistaId, reserva);
        if (error != null) {
            return new RespuestaPago(null, error);
        }

        BigDecimal monto = reserva.getMontoTotal();
        long intento = pagoRepository.countByReservaId(reservaId) + 1;
        SolicitudPago solicitud = new SolicitudPago(
                monto,
                "UMnatural - " + reserva.getHorario().getActividad().getTitulo(),
                "reserva-" + reservaId,
                "umnatural-reserva-" + reservaId + "-intento-" + intento,
                datos.token(), datos.medioPagoId(), datos.emisorId(), datos.cuotas(),
                datos.emailPagador() != null ? datos.emailPagador() : reserva.getTurista().getUserEmail(),
                datos.tipoDocumento(), datos.numeroDocumento(),
                datos.titular(), datos.ultimosDigitos());

        // Hablar con la pasarela. Si se cae o no responde, queda registrado como ERROR.
        ResultadoPago resultado;
        try {
            resultado = pasarela.cobrar(solicitud);
        } catch (RuntimeException e) {
            log.warn("Falló la pasarela al pagar la reserva {}: {}", reservaId, e.getMessage());
            resultado = new ResultadoPago(EstadoPago.ERROR, null, e.getMessage(), datos.medioPagoId(), datos.ultimosDigitos());
        }

        Pago pago = new Pago();
        pago.setReserva(reserva);
        pago.setMonto(monto);
        pago.setFecha(LocalDateTime.now());
        pago.setEstado(resultado.estado());
        pago.setIdExterno(resultado.idExterno());
        pago.setDetalle(recortar(resultado.detalle(), 500));
        pago.setMedioPago(resultado.medioPago());
        pago.setUltimosDigitos(soloUltimos4(resultado.ultimosDigitos()));
        pagoRepository.save(pago);

        if (resultado.estado() == EstadoPago.APROBADO) {
            // Se vuelve a leer por si justo venció mientras se cobraba
            Reserva actual = reservaRepository.findById(reservaId).orElseThrow();
            if (actual.getEstado() != EstadoReserva.PENDIENTE_PAGO) {
                pasarela.reembolsar(pago.getIdExterno(), monto, "umnatural-reembolso-" + pago.getId());
                pago.setMontoReembolsado(monto);
                pagoRepository.save(pago);
                return new RespuestaPago(EstadoPago.ERROR,
                        "La reserva venció mientras se procesaba el pago. Se te devolvió el dinero.");
            }
            actual.setEstado(EstadoReserva.CONFIRMADA);
            reservaRepository.save(actual);
            return new RespuestaPago(EstadoPago.APROBADO, "¡Pago aprobado! Tu reserva está confirmada.");
        }

        return new RespuestaPago(resultado.estado(), mensajePara(resultado));
    }

    // Devuelve plata del pago aprobado de una reserva (al cancelarla).
    // Devuelve el mensaje de error, o null si salió bien (o si no había nada que devolver).
    public String reembolsar(Reserva reserva, BigDecimal monto) {
        Pago pago = pagoRepository.findFirstByReservaIdAndEstado(reserva.getId(), EstadoPago.APROBADO).orElse(null);
        if (pago == null) {
            return null;
        }
        if (monto.signum() > 0) {
            boolean ok;
            try {
                ok = pasarela.reembolsar(pago.getIdExterno(), monto, "umnatural-reembolso-" + pago.getId());
            } catch (RuntimeException e) {
                ok = false;
            }
            if (!ok) {
                return "No se pudo hacer el reembolso con la pasarela. Probá de nuevo en un rato.";
            }
        }
        pago.setMontoReembolsado(monto);
        pagoRepository.save(pago);
        return null;
    }

    public List<Pago> pagosDe(Long reservaId) {
        return pagoRepository.findByReservaIdOrderByFechaDesc(reservaId);
    }

    // El último intento de pago de la reserva, o null si todavía no se intentó pagar.
    // "Mis reservas" lo usa para mostrar "Pagar" o "Reintentar pago" y cómo salió el último.
    public Pago ultimoIntento(Long reservaId) {
        List<Pago> pagos = pagoRepository.findByReservaIdOrderByFechaDesc(reservaId);
        return pagos.isEmpty() ? null : pagos.get(0);
    }

    public LocalDateTime venceElPago(Reserva reserva) {
        return reserva.inicioPlazoPago().plusHours(ReservaService.HORAS_PARA_PAGAR);
    }

    public PasarelaPago pasarela() {
        return pasarela;
    }

    // ---------- Ayuda ----------

    // Mensaje entendible para el turista según lo que contestó la pasarela
    private String mensajePara(ResultadoPago r) {
        if (r.estado() == EstadoPago.ERROR) {
            return "No pudimos comunicarnos con la pasarela de pagos. Tu lugar sigue guardado: probá de nuevo.";
        }
        if (r.estado() == EstadoPago.PENDIENTE) {
            return "El pago quedó en revisión. Te avisamos cuando se acredite; tu lugar sigue guardado.";
        }
        String detalle = r.detalle() == null ? "" : r.detalle();
        return switch (detalle) {
            case "cc_rejected_insufficient_amount" -> "Pago rechazado: la tarjeta no tiene saldo suficiente.";
            case "cc_rejected_bad_filled_security_code" -> "Pago rechazado: revisá el código de seguridad.";
            case "cc_rejected_bad_filled_date" -> "Pago rechazado: revisá la fecha de vencimiento.";
            case "cc_rejected_bad_filled_other", "cc_rejected_bad_filled_card_number" -> "Pago rechazado: revisá los datos de la tarjeta.";
            case "cc_rejected_call_for_authorize" -> "Pago rechazado: tenés que autorizarlo con tu banco.";
            default -> "El pago fue rechazado. Tu lugar sigue guardado: probá con otra tarjeta.";
        };
    }

    private String recortar(String texto, int largo) {
        if (texto == null) return null;
        return texto.length() <= largo ? texto : texto.substring(0, largo);
    }

    private String soloUltimos4(String digitos) {
        if (digitos == null) return null;
        String limpio = digitos.replaceAll("\\D", "");
        return limpio.length() <= 4 ? limpio : limpio.substring(limpio.length() - 4);
    }
}
