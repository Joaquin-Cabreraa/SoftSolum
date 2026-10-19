package uy.edu.um.proytic2026.controllers;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import uy.edu.um.proytic2026.entities.Prestador;
import uy.edu.um.proytic2026.entities.Usuario;
import uy.edu.um.proytic2026.repository.UsuarioRepository;

// Muestra las pantallas principales (las páginas HTML de la carpeta templates).
// Quién puede entrar a cada panel lo controla ControlDeAccesoInterceptor.
@Controller
public class PantallasWebController {

    private final UsuarioRepository usuarioRepository;

    public PantallasWebController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/")
    public String inicio() {
        return "inicio";               // templates/inicio.html
    }

    @GetMapping("/panel/prestador")
    public String panelPrestador(HttpSession session, Model model) {
        // El id del usuario logueado lo guardó LoginController en la sesión
        Long usuarioId = (Long) session.getAttribute("usuarioId");
        if (usuarioId == null) {
            return "redirect:/login";
        }

        Usuario usuario = usuarioRepository.findById(usuarioId).orElse(null);
        if (!(usuario instanceof Prestador prestador)) {
            return "redirect:/login";
        }

        // Datos que va a mostrar la página
        model.addAttribute("nombre", prestador.getName());
        model.addAttribute("emprendimiento", prestador.getEmprendimiento());
        return "panel-prestador";      // templates/panel-prestador.html
    }

    @GetMapping("/panel/admin")
    public String panelAdmin() {
        return "panel-admin";          // templates/panel-admin.html
    }
}
