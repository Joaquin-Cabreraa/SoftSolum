package uy.edu.um.proytic2026.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// Pone en TODAS las páginas el rol y el nombre del usuario logueado (o null si no hay sesión).
// Así la barra de arriba sabe si mostrar "Iniciar sesión" o "Mi panel / Cerrar sesión".
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
public class DatosDeSesionEnVistas {

    @org.springframework.beans.factory.annotation.Autowired
    private uy.edu.um.proytic2026.services.ResenaService resenaService;

    // Reservas que el turista ya terminó y todavía no reseñó: las páginas muestran un aviso
    @ModelAttribute("resenasPendientes")
    public java.util.List<uy.edu.um.proytic2026.entities.Reserva> resenasPendientes(HttpSession session) {
        if (!"TURISTA".equals(session.getAttribute("usuarioRol"))) return java.util.List.of();
        return resenaService.pendientesDeResena((Long) session.getAttribute("usuarioId"));
    }

    @ModelAttribute("rol")
    public Object rol(HttpSession session) {
        return session.getAttribute("usuarioRol");
    }

    @ModelAttribute("usuarioNombre")
    public Object usuarioNombre(HttpSession session) {
        return session.getAttribute("usuarioNombre");
    }

    // A qué panel lleva el botón "Mi panel" según el rol
    @ModelAttribute("urlPanel")
    public String urlPanel(HttpSession session) {
        Object rol = session.getAttribute("usuarioRol");
        if (rol == null) return null;
        if ("TURISTA".equals(rol)) return "/panel/turista";
        if ("ADMINISTRADOR".equals(rol)) return "/panel/admin";
        return "/panel/prestador";
    }
}
