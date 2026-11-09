package uy.edu.um.proytic2026.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.proytic2026.services.TiposService;

// Pantalla del administrador para mantener los tipos de actividad y de equipamiento (CU-A05).
// Cuelga de /panel/admin, así que solo entra el ADMINISTRADOR (ControlDeAccesoInterceptor).
@Controller
@RequestMapping("/panel/admin/tipos")
public class AdminTiposWebController {

    @Autowired private TiposService tiposService;

    @GetMapping
    public String verTipos(Model model) {
        model.addAttribute("tiposActividad", tiposService.tiposDeActividad());
        model.addAttribute("tiposEquipamiento", tiposService.tiposDeEquipamiento());
        return "admin-tipos";
    }

    // ---------- Tipos de actividad ----------

    @PostMapping("/actividad")
    public String crearTipoActividad(@RequestParam String nombre, RedirectAttributes redirect) {
        return resultado(tiposService.crearTipoActividad(nombre), "Tipo de actividad creado.", redirect);
    }

    @PostMapping("/actividad/{id}/editar")
    public String editarTipoActividad(@PathVariable Long id, @RequestParam String nombre, RedirectAttributes redirect) {
        return resultado(tiposService.renombrarTipoActividad(id, nombre), "Tipo de actividad modificado.", redirect);
    }

    @PostMapping("/actividad/{id}/eliminar")
    public String eliminarTipoActividad(@PathVariable Long id, RedirectAttributes redirect) {
        return resultado(tiposService.eliminarTipoActividad(id), "Tipo de actividad eliminado.", redirect);
    }

    // ---------- Tipos de equipamiento ----------

    @PostMapping("/equipamiento")
    public String crearTipoEquipamiento(@RequestParam String nombre, RedirectAttributes redirect) {
        return resultado(tiposService.crearTipoEquipamiento(nombre), "Tipo de equipamiento creado.", redirect);
    }

    @PostMapping("/equipamiento/{id}/editar")
    public String editarTipoEquipamiento(@PathVariable Long id, @RequestParam String nombre, RedirectAttributes redirect) {
        return resultado(tiposService.renombrarTipoEquipamiento(id, nombre), "Tipo de equipamiento modificado.", redirect);
    }

    @PostMapping("/equipamiento/{id}/eliminar")
    public String eliminarTipoEquipamiento(@PathVariable Long id, RedirectAttributes redirect) {
        return resultado(tiposService.eliminarTipoEquipamiento(id), "Tipo de equipamiento eliminado.", redirect);
    }

    // Muestra el error o el mensaje de éxito y vuelve a la lista
    private String resultado(String error, String exito, RedirectAttributes redirect) {
        if (error != null) {
            redirect.addFlashAttribute("error", error);
        } else {
            redirect.addFlashAttribute("exito", exito);
        }
        return "redirect:/panel/admin/tipos";
    }
}
