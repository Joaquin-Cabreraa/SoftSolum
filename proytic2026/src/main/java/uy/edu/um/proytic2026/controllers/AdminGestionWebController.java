
package uy.edu.um.proytic2026.controllers;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import uy.edu.um.proytic2026.entities.Actividad;
import uy.edu.um.proytic2026.entities.EstadoEmprendimiento;
import uy.edu.um.proytic2026.entities.VerificacionEmprendimiento;
import uy.edu.um.proytic2026.repository.ReservaRepository;
import uy.edu.um.proytic2026.services.AdministradorService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/panel/admin")
public class AdminGestionWebController {

    @Autowired
    private AdministradorService administradorService;

    @Autowired
    private ReservaRepository reservaRepository;

    // ---------- Emprendimientos ----------

    @GetMapping("/emprendimientos")
    public String emprendimientos(
            @RequestParam(required = false) String estado,
            Model model) {

        EstadoEmprendimiento filtro = convertir(estado);

        List<Map<String, Object>> emprendimientos =
                administradorService.listarEmprendimientos(filtro);

        model.addAttribute("emprendimientos", emprendimientos);
        model.addAttribute(
                "filtro",
                filtro == null ? "TODOS" : filtro.name()
        );

        // Historial de verificaciones agrupado por emprendimiento.
        Map<Long, List<VerificacionEmprendimiento>> historiales =
                new HashMap<>();

        for (Map<String, Object> emprendimiento : emprendimientos) {
            Long id = (Long) emprendimiento.get("id");

            historiales.put(
                    id,
                    administradorService.obtenerHistorial(id)
            );
        }

        model.addAttribute("historiales", historiales);

        return "admin-emprendimientos";
    }

    @PostMapping("/emprendimientos/{id}/estado")
    public String cambiarEstadoEmprendimiento(
            @PathVariable Long id,
            @RequestParam String estado,
            @RequestParam(required = false) String motivo,
            @RequestParam(required = false) String volverA,
            HttpSession session,
            RedirectAttributes redirect) {

        EstadoEmprendimiento nuevo = convertir(estado);

        Long administradorId =
                (Long) session.getAttribute("usuarioId");

        if (administradorId == null ||
                !"ADMINISTRADOR".equals(
                        session.getAttribute("usuarioRol"))) {

            return "redirect:/login";
        }

        String error =
                administradorService.cambiarEstadoEmprendimiento(
                        id,
                        nuevo,
                        administradorId,
                        motivo
                );

        if (error != null) {

            redirect.addFlashAttribute("error", error);

        } else {

            redirect.addFlashAttribute(
                    "exito",
                    switch (nuevo) {
                        case ACEPTADO ->
                                "Emprendimiento aceptado: ya puede publicar actividades.";

                        case DENEGADO ->
                                "Emprendimiento denegado: sus actividades ya no se muestran en el portal.";

                        default ->
                                "El emprendimiento volvió a quedar pendiente de revisión.";
                    }
            );
        }

        return "redirect:/panel/admin/emprendimientos"
                + (volverA == null || volverA.isBlank()
                ? ""
                : "?estado=" + volverA);
    }

    // ---------- Actividades ----------

    @GetMapping("/actividades")
    public String actividades(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado,
            Model model,
            HttpSession session) {

        model.addAttribute(
                "correoAdmin",
                session.getAttribute("usuarioEmail")
        );

        List<Actividad> actividades =
                administradorService.listarActividades(q, estado);

        Set<Long> conReservas = new HashSet<>();

        for (Actividad a : actividades) {
            if (reservaRepository.actividadTieneReservas(a.getId())) {
                conReservas.add(a.getId());
            }
        }

        model.addAttribute("actividades", actividades);
        model.addAttribute("conReservas", conReservas);
        model.addAttribute("q", q);
        model.addAttribute("estado", estado);

        return "admin-actividades";
    }

    @PostMapping("/actividades/{id}/estado")
    public String cambiarEstadoActividad(
            @PathVariable Long id,
            @RequestParam String estado,
            @RequestParam(required = false) String motivo,
            @RequestParam(required = false) String contacto,
            RedirectAttributes redirect) {

        String error =
                administradorService.cambiarEstadoActividad(
                        id,
                        estado,
                        motivo,
                        contacto
                );

        if (error != null) {

            redirect.addFlashAttribute("error", error);

        } else {

            redirect.addFlashAttribute(
                    "exito",
                    "BAJA".equals(estado)
                            ? "Actividad quitada del portal. El prestador ve el motivo y tu correo de contacto; no la puede volver a publicar por su cuenta."
                            : "Actividad publicada de nuevo (se ve en el portal si su emprendimiento está aceptado)."
            );
        }

        return "redirect:/panel/admin/actividades";
    }

    private EstadoEmprendimiento convertir(String estado) {

        if (estado == null
                || estado.isBlank()
                || "TODOS".equalsIgnoreCase(estado)) {
            return null;
        }

        try {
            return EstadoEmprendimiento.valueOf(
                    estado.toUpperCase()
            );

        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
