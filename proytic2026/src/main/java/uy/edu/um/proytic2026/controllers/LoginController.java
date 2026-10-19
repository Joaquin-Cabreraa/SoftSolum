package uy.edu.um.proytic2026.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.services.UsuarioService;

@Controller
public class LoginController {

    private final UsuarioService usuarioService;

    public LoginController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/login")
    public String mostrarLogin() {
        return "login";
    }

    @PostMapping("/login")
    public String iniciarSesion(@RequestParam String email,
                                @RequestParam String password,
                                HttpServletRequest request,
                                Model model) {
        Usuario usuario = usuarioService.autenticar(email, password).orElse(null);

        if (usuario == null) {
            model.addAttribute("error", "El correo o la contraseña son incorrectos.");
            model.addAttribute("email", email);
            return "login";
        }

        // Evita conservar atributos de una sesión anterior al autenticar de nuevo.
        HttpSession sesionAnterior = request.getSession(false);
        if (sesionAnterior != null) {
            sesionAnterior.invalidate();
        }

        // Guardamos en sesión los datos que el resto de la aplicación podrá consultar.
        HttpSession nuevaSesion = request.getSession(true);
        nuevaSesion.setAttribute("usuarioId", usuario.getId());
        nuevaSesion.setAttribute("usuarioNombre", usuario.getName());
        nuevaSesion.setAttribute("usuarioEmail", usuario.getUserEmail());
        nuevaSesion.setAttribute("usuarioRol", obtenerRol(usuario));

        // La navegación posterior al login se integrará con el resto del equipo.
        model.addAttribute("nombreUsuario", usuario.getName());
        model.addAttribute("rolUsuario", obtenerRol(usuario));
        model.addAttribute("loginExitoso", true);
        return "login";
    }

    @GetMapping("/logout")
    public String cerrarSesion(HttpSession session) {
        session.invalidate();
        return "redirect:/login?logout";
    }

    private String obtenerRol(Usuario usuario) {
        if (usuario instanceof Administrador) return "ADMINISTRADOR";
        if (usuario instanceof Empresa) return "EMPRESA";
        if (usuario instanceof Particular) return "PARTICULAR";
        if (usuario instanceof Prestador) return "PRESTADOR";
        return "TURISTA";
    }
}
