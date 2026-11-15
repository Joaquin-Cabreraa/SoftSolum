package uy.edu.um.proytic2026.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.pasarela.PasarelaClient;
import uy.edu.um.proytic2026.pasarela.RespuestaPasarela;
import uy.edu.um.proytic2026.pasarela.SolicitudPasarela;
import uy.edu.um.proytic2026.repository.PagoRepository;
import uy.edu.um.proytic2026.repository.ReservaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

// Pago de reservas con una pasarela externa. CU-T07 Pagar reserva.
//
// Regla de oro del obligatorio: NUNCA se confirma una reserva si el pago no fue procesado.
// Solo un pago APROBADO por la pasarela pasa la reserva a CONFIRMADA. Si la pasarela rechaza,
// no responde (timeout) o está caída, la reserva sigue PENDIENTE_PAGO y se puede reintentar
// mientras no pasen las 2 horas (RNE #2). Cada intento queda guardado (RNE #14).
@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private PasarelaClient pasarelaClient;

    // ---------- Validación ----------

    // ¿Esta reserva se puede pagar ahora? (pendiente y dentro de las 2 horas).
    // Se usa en Mis reservas para mostrar o esconder el botón "Pagar".
    public boolean sePuedePagar(Reserva reserva) {
        return reserva.getEstado() == EstadoReserva.PENDIENTE_PAGO
                && LocalDateTime.now().isBefore(reserva.getFechaCreacion().plusHours(ReservaService.HORAS_PARA_PAGAR));
    }

    // Revisa que la reserva exista, sea del turista y se pueda pagar.
    // Devuelve el mensaje de error, o null si está todo bien.
    public String validarReservaPagable(Long turistaId, Reserva reserva) {
        if (reserva == null || turistaId == null || reserva.getTurista() == null
                || !turistaId.equals(reserva.getTurista().getId())) {
            return "La reserva no existe.";
        }
        if (reserva.getEstado() == EstadoReserva.CONFIRMADA) {
            return "Esta reserva ya está pagada.";
        }
        if (reserva.getEstado() == EstadoReserva.CANCELADA) {
            return "Esta reserva está cancelada, ya no se puede pagar.";
        }
        if (!sePuedePagar(reserva)) {
            return "Venció el plazo de " + ReservaService.HORAS_PARA_PAGAR + " horas para pagar esta reserva.";
        }
        return null;
    }

    // Revisa el formulario de pago completo. Devuelve el mensaje de error, o null si está bien.
    // (Mismo estilo que ReservaService.validarReserva)
    public String validarPago(Long turistaId, Long reservaId, String numeroTarjeta, String vencimiento, String cvv) {
        Reserva reserva = reservaId == null ? null : reservaRepository.findById(reservaId).orElse(null);

        String error = validarReservaPagable(turistaId, reserva);
        if (error != null) {
            return error;
        }
        return validarTarjeta(numeroTarjeta, vencimiento, cvv);
    }

    // Controles del formulario de la tarjeta (solo de formato; la que decide es la pasarela)
    public String validarTarjeta(String numeroTarjeta, String vencimiento, String cvv) {
        if (vacio(numeroTarjeta) || !soloDigitos(numeroTarjeta).matches("\\d{13,19}")) {
            return "El número de tarjeta no es válido (tiene que tener entre 13 y 19 dígitos).";
        }

        if (vacio(vencimiento) || !vencimiento.trim().matches("(0[1-9]|1[0-2])/(\\d{2}|\\d{4})")) {
            return "El vencimiento tiene que tener el formato MM/AA.";
        }
        String[] partes = vencimiento.trim().split("/");
        int mes = Integer.parseInt(partes[0]);
        int anio = Integer.parseInt(partes[1]);
        if (anio < 100) {
            anio += 2000;
        }
        if (YearMonth.of(anio, mes).isBefore(YearMonth.now())) {
            return "La tarjeta está vencida.";
        }

        if (vacio(cvv) || !cvv.trim().matches("\\d{3,4}")) {
            return "El código de seguridad (CVV) tiene que tener 3 o 4 dígitos.";
        }
        return null;
    }

    // ---------- Pagar ----------

    // Intenta cobrar la reserva con la tarjeta. Devuelve el Pago que quedó guardado
    // (APROBADO, RECHAZADO o ERROR), o null si no se llegó a intentar porque los datos
    // no son válidos o la reserva no se puede pagar (llamar antes a validarPago para saber por qué).
    //
    // @Transactional + bloqueo de la reserva: si el turista hace doble clic, el segundo pago espera
    // al primero y después ve la reserva ya CONFIRMADA, así que no se cobra dos veces.
    // Esta función NUNCA lanza excepción por culpa de la pasarela: cualquier falla de la
    // llamada HTTP (timeout, caída, respuesta rara) se guarda como un Pago en estado ERROR.
    @Transactional
    public Pago pagar(Long turistaId, Long reservaId, String numeroTarjeta, String vencimiento, String cvv) {
        Reserva reserva = reservaId == null ? null : reservaRepository.findByIdParaPagar(reservaId).orElse(null);

        if (validarReservaPagable(turistaId, reserva) != null
                || validarTarjeta(numeroTarjeta, vencimiento, cvv) != null) {
            return null;
        }

        String numero = soloDigitos(numeroTarjeta);

        Pago pago = new Pago();
        pago.setReserva(reserva);
        pago.setTipo(TipoPago.COBRO);
        pago.setMonto(reserva.getMontoTotal());          // precio x cupos, calculado al reservar (RNE #11)
        pago.setFecha(LocalDateTime.now());
        pago.setUltimos4(numero.substring(numero.length() - 4));

        // Llamada HTTP a la pasarela (con timeout). Pase lo que pase, el intento se guarda.
        try {
            RespuestaPasarela respuesta = pasarelaClient.cobrar(
                    new SolicitudPasarela(numero, vencimiento.trim(), cvv.trim(), reserva.getMontoTotal()));

            if (respuesta != null && "APROBADO".equals(respuesta.estado())) {
                pago.setEstado(EstadoPago.APROBADO);
            } else if (respuesta != null && "RECHAZADO".equals(respuesta.estado())) {
                pago.setEstado(EstadoPago.RECHAZADO);
            } else {
                pago.setEstado(EstadoPago.ERROR);
                pago.setDetalle("La pasarela devolvió una respuesta que no se entiende.");
            }
            if (respuesta != null) {
                pago.setCodigoPasarela(respuesta.codigo());
                if (pago.getEstado() != EstadoPago.ERROR) {
                    pago.setDetalle(recortar(respuesta.mensaje()));
                }
            }
        } catch (RuntimeException e) {
            // Timeout, pasarela caída (5xx), sin conexión, JSON inválido...
            log.warn("La pasarela no pudo procesar el pago de la reserva {}: {}", reserva.getId(), e.getMessage());
            pago.setEstado(EstadoPago.ERROR);
            pago.setDetalle("La pasarela no respondió a tiempo o no está disponible.");
        }

        pagoRepository.save(pago);
        reserva.getPagos().add(pago);

        // SOLO un pago aprobado confirma la reserva
        if (pago.getEstado() == EstadoPago.APROBADO) {
            reserva.setEstado(EstadoReserva.CONFIRMADA);
            reservaRepository.save(reserva);
        }
        return pago;
    }

    // ---------- Reembolso ----------

    // Registra la devolución de un porcentaje del pago (para la cancelación de una reserva
    // confirmada, que decide el porcentaje según la política del horario).
    // Monto devuelto = monto del cobro aprobado x porcentaje / 100, redondeado a 2 decimales.
    // Devuelve el Pago de tipo REEMBOLSO, o null si no hay nada para devolver:
    // porcentaje 0, la reserva nunca se pagó, o ya se había reembolsado (no se devuelve dos veces).
    // Lanza IllegalArgumentException si el porcentaje no está entre 0 y 100.
    @Transactional
    public Pago reembolsar(Reserva reserva, int porcentaje) {
        if (porcentaje < 0 || porcentaje > 100) {
            throw new IllegalArgumentException("El porcentaje de reembolso tiene que estar entre 0 y 100.");
        }
        if (porcentaje == 0) {
            return null;
        }

        Pago cobro = pagoRepository
                .findFirstByReservaIdAndTipoAndEstado(reserva.getId(), TipoPago.COBRO, EstadoPago.APROBADO)
                .orElse(null);
        if (cobro == null
                || pagoRepository.existsByReservaIdAndTipoAndEstado(reserva.getId(), TipoPago.REEMBOLSO, EstadoPago.APROBADO)) {
            return null;
        }

        Pago devolucion = new Pago();
        devolucion.setReserva(reserva);
        devolucion.setTipo(TipoPago.REEMBOLSO);
        devolucion.setEstado(EstadoPago.APROBADO);
        devolucion.setMonto(cobro.getMonto().multiply(BigDecimal.valueOf(porcentaje))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        devolucion.setFecha(LocalDateTime.now());
        devolucion.setUltimos4(cobro.getUltimos4());
        devolucion.setCodigoPasarela("REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        devolucion.setDetalle("Reembolso del " + porcentaje + "% del pago");

        pagoRepository.save(devolucion);
        if (reserva.getPagos() != null) {
            reserva.getPagos().add(devolucion);
        }
        return devolucion;
    }

    // ---------- Consultar ----------

    // Todos los pagos de una reserva (cobros y reembolsos), del más viejo al más nuevo
    @Transactional(readOnly = true)
    public List<Pago> pagosDeLaReserva(Long reservaId) {
        return pagoRepository.findByReservaIdOrderByFechaAscIdAsc(reservaId);
    }

    // El último intento de COBRO de la reserva, o null si todavía no se intentó pagar.
    // Mis reservas lo usa para decir "Pagar" o "Reintentar pago" y mostrar cómo salió el último.
    public Pago ultimoCobro(Reserva reserva) {
        Pago ultimo = null;
        for (Pago pago : reserva.getPagos()) {
            if (pago.getTipo() == TipoPago.COBRO) {
                ultimo = pago;
            }
        }
        return ultimo;
    }

    // ---------- Métodos de ayuda ----------

    private boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    // Saca espacios y guiones: "4111 1111-1111 1111" -> "4111111111111111"
    private String soloDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("[\\s\\-]", "");
    }

    private String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= 200 ? texto : texto.substring(0, 200);
    }
}
