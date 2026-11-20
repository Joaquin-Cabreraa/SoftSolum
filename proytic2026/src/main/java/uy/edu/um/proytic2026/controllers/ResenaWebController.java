package uy.edu.um.proytic2026.controllers;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.proytic2026.entities.Emprendimiento;
import uy.edu.um.proytic2026.entities.Reserva;
import uy.edu.um.proytic2026.repository.ReservaRepository;
import uy.edu.um.proytic2026.services.ActividadService;
import uy.edu.um.proytic2026.services.ResenaService;

import java.time.LocalDateTime;

// Reseñas: el turista las deja (/panel/turista), el prestador responde (/panel/prestador)
// y el administrador modera (/panel/admin). El interceptor controla quién entra a cada parte.
@Controller
public class ResenaWebController {

    @Autowired private ResenaService resenaService;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private ActividadService actividadService;

    private Long usuarioId(HttpSession session) {
        return (Long) session.getAttribute("usuarioId");
    }

    // ---------- Turista ----------

    @GetMapping("/panel/turista/reservas/{id}/resena")
    public String formulario(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes redirect) {
        Reserva reserva = reservaRepository.findById(id).orElse(null);
        String error = resenaService.puedeResenar(usuarioId(session), reserva, LocalDateTime.now());
        if (error != null) {
            redirect.addFlashAttribute("error", error);
            return "redirect:/panel/turista/reservas";
        }
        model.addAttribute("reserva", reserva);
        return "resena";
    }

    @PostMapping("/panel/turista/reservas/{id}/resena")
    public String guardar(@PathVariable Long id, @RequestParam(required = false) Integer puntaje,
                          @RequestParam(required = false) String comentario,
                          HttpSession session, Model model, RedirectAttributes redirect) {
        String error = resenaService.crear(usuarioId(session), id, puntaje, comentario);
        if (error != null) {
            Reserva reserva = reservaRepository.findById(id).orElse(null);
            if (reserva == null) {
                redirect.addFlashAttribute("error", error);
                return "redirect:/panel/turista/reservas";
            }
            model.addAttribute("reserva", reserva);
            model.addAttribute("error", error);
            model.addAttribute("comentario", comentario);
            return "resena";
        }
        redirect.addFlashAttribute("exito", "¡Gracias por tu reseña! Ya la pueden ver otros turistas.");
        return "redirect:/panel/turista/reservas";
    }

    // ---------- Prestador ----------

    @GetMapping("/panel/prestador/resenas")
    public String delPrestador(HttpSession session, Model model) {
        Emprendimiento emp = actividadService.emprendimientoDe(usuarioId(session));
        model.addAttribute("resenas", resenaService.delEmprendimiento(emp == null ? null : emp.getId()));
        return "prestador-resenas";
    }

    @PostMapping("/panel/prestador/resenas/{id}/responder")
    public String responder(@PathVariable Long id, @RequestParam(required = false) String respuesta,
                            HttpSession session, RedirectAttributes redirect) {
        Emprendimiento emp = actividadService.emprendimientoDe(usuarioId(session));
        String error = resenaService.responder(emp == null ? null : emp.getId(), id, respuesta);
        redirect.addFlashAttribute(error != null ? "error" : "exito", error != null ? error : "Respuesta publicada.");
        return "redirect:/panel/prestador/resenas";
    }

    // ---------- Administrador ----------

    @GetMapping("/panel/admin/resenas")
    public String paraAdmin(@RequestParam(defaultValue = "false") boolean ocultas, Model model) {
        model.addAttribute("resenas", resenaService.todas(ocultas));
        model.addAttribute("soloOcultas", ocultas);
        return "admin-resenas";
    }

    @PostMapping("/panel/admin/resenas/{id}/ocultar")
    public String ocultar(@PathVariable Long id, @RequestParam(required = false) String motivo, RedirectAttributes redirect) {
        String error = resenaService.moderar(id, true, motivo);
        redirect.addFlashAttribute(error != null ? "error" : "exito", error != null ? error : "Reseña oculta: ya no se ve en el portal.");
        return "redirect:/panel/admin/resenas";
    }

    @PostMapping("/panel/admin/resenas/{id}/mostrar")
    public String mostrar(@PathVariable Long id, RedirectAttributes redirect) {
        String error = resenaService.moderar(id, false, null);
        redirect.addFlashAttribute(error != null ? "error" : "exito", error != null ? error : "La reseña se vuelve a mostrar.");
        return "redirect:/panel/admin/resenas";
    }

    @PostMapping("/panel/admin/resenas/{id}/quitar-respuesta")
    public String quitarRespuesta(@PathVariable Long id, RedirectAttributes redirect) {
        String error = resenaService.quitarRespuesta(id);
        redirect.addFlashAttribute(error != null ? "error" : "exito", error != null ? error : "Respuesta del prestador quitada.");
        return "redirect:/panel/admin/resenas";
    }
}
