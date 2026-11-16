package uy.edu.um.proytic2026.controllers;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.proytic2026.entities.Reserva;
import uy.edu.um.proytic2026.pagos.SolicitudPago;
import uy.edu.um.proytic2026.repository.ReservaRepository;
import uy.edu.um.proytic2026.services.PagoService;

import java.util.LinkedHashMap;
import java.util.Map;

// Pantalla de pago de una reserva (CU-T07). Cuelga de /panel/turista: solo turistas.
// El turista paga SIN salir de UMnatural: la tarjeta la toma el formulario de Mercado Pago
// (Card Payment Brick) dentro de nuestra página, la convierte en un token y nos manda solo el token.
@Controller
@RequestMapping("/panel/turista/reservas/{id}/pagar")
public class PagoWebController {

    @Autowired private PagoService pagoService;
    @Autowired private ReservaRepository reservaRepository;

    @GetMapping
    public String pantallaDePago(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes redirect) {
        Long turistaId = (Long) session.getAttribute("usuarioId");
        Reserva reserva = reservaRepository.findById(id).orElse(null);

        String error = pagoService.validarPago(turistaId, reserva);
        if (error != null) {
            redirect.addFlashAttribute("error", error);
            return "redirect:/panel/turista/reservas";
        }

        model.addAttribute("reserva", reserva);
        model.addAttribute("actividad", reserva.getHorario().getActividad());
        model.addAttribute("vence", pagoService.venceElPago(reserva));
        model.addAttribute("intentos", pagoService.pagosDe(id));
        model.addAttribute("pasarela", pagoService.pasarela().nombre());
        model.addAttribute("clavePublica", pagoService.pasarela().clavePublica());
        model.addAttribute("emailTurista", reserva.getTurista().getUserEmail());
        return "pagar";                  // templates/pagar.html
    }

    // Lo llama el JavaScript de la página (fetch) con el token de la tarjeta.
    // Contesta JSON: { estado: "APROBADO" | "RECHAZADO" | ..., mensaje: "..." }
    @PostMapping
    @ResponseBody
    public Map<String, Object> pagar(@PathVariable Long id, @RequestBody Map<String, Object> datos, HttpSession session) {
        Long turistaId = (Long) session.getAttribute("usuarioId");

        // Lo que manda el Brick de Mercado Pago: token, payment_method_id, issuer_id,
        // installments y payer { email, identification { type, number } }.
        // En la pasarela simulada: titular y ultimosDigitos.
        Map<?, ?> pagador = datos.get("payer") instanceof Map<?, ?> p ? p : Map.of();
        Map<?, ?> documento = pagador.get("identification") instanceof Map<?, ?> d ? d : Map.of();

        SolicitudPago solicitud = new SolicitudPago(null, null, null, null,
                texto(datos.get("token")),
                texto(datos.get("payment_method_id")),
                texto(datos.get("issuer_id")),
                entero(datos.get("installments")),
                texto(pagador.get("email")),
                texto(documento.get("type")),
                texto(documento.get("number")),
                texto(datos.get("titular")),
                texto(datos.get("ultimosDigitos")));

        PagoService.RespuestaPago respuesta = pagoService.pagar(turistaId, id, solicitud);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("estado", respuesta.estado() == null ? "NO_PERMITIDO" : respuesta.estado().name());
        json.put("aprobado", respuesta.aprobado());
        json.put("mensaje", respuesta.mensaje());
        if (respuesta.aprobado()) {
            // Después de pagar se muestra el comprobante con el código QR
            reservaRepository.findById(id).ifPresent(r -> json.put("comprobante", "/reserva/" + r.getCodigo()));
        }
        return json;
    }

    private String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }

    private Integer entero(Object valor) {
        if (valor instanceof Number n) return n.intValue();
        try {
            return valor == null ? null : Integer.parseInt(String.valueOf(valor));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
