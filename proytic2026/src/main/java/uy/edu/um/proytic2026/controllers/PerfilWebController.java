package uy.edu.um.proytic2026.controllers;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.proytic2026.entities.Empresa;
import uy.edu.um.proytic2026.entities.Particular;
import uy.edu.um.proytic2026.entities.Prestador;
import uy.edu.um.proytic2026.entities.Turista;
import uy.edu.um.proytic2026.entities.Usuario;
import uy.edu.um.proytic2026.services.PerfilService;

// "Mis datos" del turista y del prestador.
// Las rutas cuelgan de /panel/turista y /panel/prestador, así que
// ControlDeAccesoInterceptor ya controla que cada uno entre solo a lo suyo.
@Controller
public class PerfilWebController {

    @Autowired
    private PerfilService perfilService;

    private Long usuarioId(HttpSession session) {
        return (Long) session.getAttribute("usuarioId");
    }

    // ---------- Turista ----------

    @GetMapping("/panel/turista/datos")
    public String datosTurista(HttpSession session, Model model) {
        if (!(perfilService.usuario(usuarioId(session)) instanceof Turista turista)) {
            return "redirect:/login";
        }
        model.addAttribute("turista", turista);
        return "mis-datos-turista";
    }

    @PostMapping("/panel/turista/datos")
    public String guardarTurista(@RequestParam(required = false) String nombre,
                                 @RequestParam(required = false) String apellido,
                                 @RequestParam(required = false) String documento,
                                 @RequestParam(required = false) String fechaNacimiento,
                                 @RequestParam(required = false) String email,
                                 @RequestParam(required = false) String telefono,
                                 HttpSession session, RedirectAttributes redirect) {
        String error = perfilService.actualizarTurista(usuarioId(session), nombre, apellido, documento,
                fechaNacimiento, email, telefono);
        return resultado(error, "Tus datos se guardaron.", "/panel/turista/datos", session, redirect);
    }

    @PostMapping("/panel/turista/datos/contrasena")
    public String contrasenaTurista(@RequestParam(required = false) String actual,
                                    @RequestParam(required = false) String nueva,
                                    @RequestParam(required = false) String confirmacion,
                                    HttpSession session, RedirectAttributes redirect) {
        String error = perfilService.cambiarContrasena(usuarioId(session), actual, nueva, confirmacion);
        return resultado(error, "Contraseña cambiada.", "/panel/turista/datos", session, redirect);
    }

    // ---------- Prestador ----------

    @GetMapping("/panel/prestador/datos")
    public String datosPrestador(HttpSession session, Model model) {
        if (!(perfilService.usuario(usuarioId(session)) instanceof Prestador prestador)) {
            return "redirect:/login";
        }
        model.addAttribute("prestador", prestador);
        model.addAttribute("emprendimiento", prestador.getEmprendimiento());
        model.addAttribute("esEmpresa", prestador instanceof Empresa);
        // Identificación verificada por el administrador: se muestra pero no se edita
        if (prestador instanceof Empresa empresa) {
            model.addAttribute("identificacion", "RUT " + empresa.getRut());
        } else if (prestador instanceof Particular particular) {
            model.addAttribute("identificacion", "Cédula " + particular.getDocumento());
        }
        return "mis-datos-prestador";
    }

    @PostMapping("/panel/prestador/datos")
    public String guardarPrestador(@RequestParam(required = false) String nombre,
                                   @RequestParam(required = false) String apellido,
                                   @RequestParam(required = false) String email,
                                   @RequestParam(required = false) String telefono,
                                   HttpSession session, RedirectAttributes redirect) {
        String error = perfilService.actualizarPrestador(usuarioId(session), nombre, apellido, email, telefono);
        return resultado(error, "Tus datos se guardaron.", "/panel/prestador/datos", session, redirect);
    }

    @PostMapping("/panel/prestador/datos/emprendimiento")
    public String guardarEmprendimiento(@RequestParam(required = false) String nombre,
                                        @RequestParam(required = false) String ubicacion,
                                        @RequestParam(required = false) String email,
                                        @RequestParam(required = false) String telefono,
                                        @RequestParam(required = false) String descripcion,
                                        HttpSession session, RedirectAttributes redirect) {
        String error = perfilService.actualizarEmprendimiento(usuarioId(session), nombre, ubicacion, email,
                telefono, descripcion);
        return resultado(error, "Los datos del emprendimiento se guardaron.", "/panel/prestador/datos", session, redirect);
    }

    @PostMapping("/panel/prestador/datos/contrasena")
    public String contrasenaPrestador(@RequestParam(required = false) String actual,
                                      @RequestParam(required = false) String nueva,
                                      @RequestParam(required = false) String confirmacion,
                                      HttpSession session, RedirectAttributes redirect) {
        String error = perfilService.cambiarContrasena(usuarioId(session), actual, nueva, confirmacion);
        return resultado(error, "Contraseña cambiada.", "/panel/prestador/datos", session, redirect);
    }

    // Mensaje y vuelta a la página. Si cambió el nombre o el correo, se actualiza la sesión.
    private String resultado(String error, String exito, String volverA, HttpSession session, RedirectAttributes redirect) {
        if (error != null) {
            redirect.addFlashAttribute("error", error);
        } else {
            redirect.addFlashAttribute("exito", exito);
            Usuario usuario = perfilService.usuario(usuarioId(session));
            if (usuario != null) {
                session.setAttribute("usuarioNombre", usuario.getName());
                session.setAttribute("usuarioEmail", usuario.getUserEmail());
            }
        }
        return "redirect:" + volverA;
    }
}
