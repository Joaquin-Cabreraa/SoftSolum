package uy.edu.um.proytic2026.controllers;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.proytic2026.entities.EstadoPago;
import uy.edu.um.proytic2026.entities.Pago;
import uy.edu.um.proytic2026.entities.Reserva;
import uy.edu.um.proytic2026.entities.TipoPago;
import uy.edu.um.proytic2026.repository.ReservaRepository;
import uy.edu.um.proytic2026.services.PagoService;
import uy.edu.um.proytic2026.services.ReservaService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Pantalla de pago de una reserva (CU-T07). Cuelga de /panel/turista, así que
// ControlDeAccesoInterceptor solo deja pasar a usuarios con rol TURISTA.
// Se llega desde "Reservar" (al crear la reserva) y desde el botón "Pagar" / "Reintentar pago" de Mis reservas.
@Controller
public class PagoWebController {

    private static final DateTimeFormatter HORA_FECHA = DateTimeFormatter.ofPattern("HH:mm 'del' dd/MM");

    @Autowired
    private PagoService pagoService;

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private ReservaRepository reservaRepository;

    // Formulario de pago (número de tarjeta, vencimiento, CVV)
    @GetMapping("/panel/turista/reservas/{id}/pagar")
    public String formularioPago(@PathVariable Long id, HttpSession session,
                                 Model model, RedirectAttributes redirect) {
        Long turistaId = (Long) session.getAttribute("usuarioId");
        Reserva reserva = reservaRepository.findById(id).orElse(null);

        String error = pagoService.validarReservaPagable(turistaId, reserva);
        if (error != null) {
            redirect.addFlashAttribute("error", error);
            return "redirect:/panel/turista/reservas";
        }

        cargarModelo(model, reserva);
        return "pago";                   // templates/pago.html
    }

    // Recibe la tarjeta, intenta el cobro y muestra un mensaje claro según el resultado
    @PostMapping("/panel/turista/reservas/{id}/pagar")
    public String pagar(@PathVariable Long id,
                        @RequestParam(required = false) String numeroTarjeta,
                        @RequestParam(required = false) String vencimiento,
                        @RequestParam(required = false) String cvv,
                        HttpSession session, Model model, RedirectAttributes redirect) {

        Long turistaId = (Long) session.getAttribute("usuarioId");
        Reserva reserva = reservaRepository.findById(id).orElse(null);

        // 1. ¿La reserva se puede pagar? (es de este turista, está pendiente y no venció)
        String error = pagoService.validarReservaPagable(turistaId, reserva);
        if (error != null) {
            redirect.addFlashAttribute("error", error);
            return "redirect:/panel/turista/reservas";
        }

        // 2. ¿Los datos de la tarjeta tienen formato válido? Si no, volvemos al formulario.
        //    (Nunca devolvemos el número ni el CVV a la página.)
        error = pagoService.validarTarjeta(numeroTarjeta, vencimiento, cvv);
        if (error != null) {
            cargarModelo(model, reserva);
            model.addAttribute("error", error);
            return "pago";
        }

        // 3. Cobrar (llama a la pasarela con timeout y guarda el intento)
        Pago pago = pagoService.pagar(turistaId, id, numeroTarjeta, vencimiento, cvv);
        if (pago == null) {
            // Pasó algo entre medio (ej. la reserva venció o se pagó en otra pestaña)
            redirect.addFlashAttribute("error", "No se pudo realizar el pago. Revisá el estado de tu reserva.");
            return "redirect:/panel/turista/reservas";
        }

        // 4. Un mensaje para cada resultado
        if (pago.getEstado() == EstadoPago.APROBADO) {
            redirect.addFlashAttribute("exito", "¡Pago aprobado! Tu reserva quedó confirmada.");
            return "redirect:/panel/turista/reservas";
        }

        // Rechazado o error: la reserva sigue pendiente y se puede reintentar
        Reserva actualizada = reservaRepository.findById(id).orElse(reserva);
        String limite = HORA_FECHA.format(reservaService.venceElPago(actualizada));

        if (pago.getEstado() == EstadoPago.RECHAZADO) {
            model.addAttribute("error", "Tu pago fue rechazado: " + minuscula(pago.getDetalle())
                    + ". Tu reserva sigue pendiente: podés probar con otra tarjeta hasta las " + limite + ".");
        } else {
            model.addAttribute("error", "El servicio de pagos no respondió, así que no pudimos confirmar tu pago "
                    + "y tu reserva sigue pendiente. Probá de nuevo en unos minutos; tenés tiempo hasta las " + limite + ".");
        }
        cargarModelo(model, actualizada);
        return "pago";
    }

    // Datos que necesita pago.html
    private void cargarModelo(Model model, Reserva reserva) {
        List<Pago> intentos = pagoService.pagosDeLaReserva(reserva.getId()).stream()
                .filter(p -> p.getTipo() == TipoPago.COBRO)
                .toList();

        model.addAttribute("reserva", reserva);
        model.addAttribute("actividad", reserva.getHorario().getActividad());
        model.addAttribute("horario", reserva.getHorario());
        model.addAttribute("venceElPago", reservaService.venceElPago(reserva));
        model.addAttribute("intentos", intentos);
    }

    private String minuscula(String texto) {
        if (texto == null || texto.isBlank()) {
            return "la tarjeta no fue aceptada";
        }
        return texto.substring(0, 1).toLowerCase() + texto.substring(1);
    }
}
