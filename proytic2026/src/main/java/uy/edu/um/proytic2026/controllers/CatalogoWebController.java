package uy.edu.um.proytic2026.controllers;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import uy.edu.um.proytic2026.entities.Actividad;
import uy.edu.um.proytic2026.services.CatalogoService;

import java.math.BigDecimal;
import java.time.LocalDate;

// Páginas públicas del portal: cualquiera puede verlas sin iniciar sesión
// (el enunciado: "un visitante podrá consultar las actividades ... sin necesidad de registrarse").
@Controller
public class CatalogoWebController {

    @Autowired
    private CatalogoService catalogoService;

    // Explorar y buscar actividades (CU-T04). Todos los filtros son opcionales.
    // Ej: /actividades?q=kayak&departamento=Rocha&precioMaximo=2000
    @GetMapping("/actividades")
    public String explorar(@RequestParam(required = false) String q,
                           @RequestParam(required = false) String departamento,
                           @RequestParam(required = false) Long tipo,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                           @RequestParam(required = false) BigDecimal precioMaximo,
                           HttpSession session, Model model) {

        model.addAttribute("actividades", catalogoService.buscar(q, departamento, tipo, fecha, precioMaximo));

        // Opciones de los desplegables
        model.addAttribute("departamentos", catalogoService.departamentosConActividades());
        model.addAttribute("tipos", catalogoService.tiposConActividades());

        // Lo que escribió el usuario, para que no se borre del formulario
        model.addAttribute("q", q);
        model.addAttribute("departamentoElegido", departamento);
        model.addAttribute("tipoElegido", tipo);
        model.addAttribute("fecha", fecha);
        model.addAttribute("precioMaximo", precioMaximo);

        model.addAttribute("rol", session.getAttribute("usuarioRol"));
        return "actividades";            // templates/actividades.html
    }

    // Detalle de una actividad con sus próximos horarios (CU-T05)
    @GetMapping("/actividades/{id}")
    public String detalle(@PathVariable Long id, HttpSession session, Model model) {
        Actividad actividad = catalogoService.obtenerActividadPublicada(id);
        if (actividad == null) {
            // No existe o su emprendimiento no está aceptado: para el público "no existe"
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        model.addAttribute("actividad", actividad);
        model.addAttribute("horarios", catalogoService.proximosHorarios(actividad));
        model.addAttribute("rol", session.getAttribute("usuarioRol"));
        return "actividad-detalle";     // templates/actividad-detalle.html
    }
}
