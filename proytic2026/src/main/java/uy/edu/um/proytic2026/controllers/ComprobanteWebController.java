package uy.edu.um.proytic2026.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uy.edu.um.proytic2026.entities.Reserva;
import uy.edu.um.proytic2026.services.ReservaService;

// Comprobante de una reserva con su código QR.
// Es público a propósito: el prestador escanea el QR el día de la actividad y ve los datos
// sin tener que iniciar sesión. Para entrar hay que tener el código (un UUID imposible de adivinar).
@Controller
public class ComprobanteWebController {

    @Autowired
    private ReservaService reservaService;

    // Dirección pública de la app para armar el link del QR (variable de entorno APP_URL).
    // Si está vacía se usa la dirección con la que se entró (ej. http://localhost:8080).
    @Value("${app.url-publica:}")
    private String urlPublica;

    @GetMapping("/reserva/{codigo}")
    public String comprobante(@PathVariable String codigo, Model model) {
        Reserva reserva = reservaService.reservaPorCodigo(codigo);
        if (reserva == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("reserva", reserva);
        model.addAttribute("actividad", reserva.getHorario().getActividad());
        model.addAttribute("urlQr", urlBase() + "/reserva/" + codigo);
        model.addAttribute("codigoCorto", codigo.substring(0, 8).toUpperCase());
        return "comprobante";
    }

    private String urlBase() {
        if (urlPublica != null && !urlPublica.isBlank()) {
            return urlPublica.replaceAll("/+$", "");
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    // Tapa el documento salvo los últimos 3: "12345672" -> "•••••672"
    public static String ocultar(String documento) {
        if (documento == null || documento.length() <= 3) return documento;
        return "•".repeat(documento.length() - 3) + documento.substring(documento.length() - 3);
    }
}
