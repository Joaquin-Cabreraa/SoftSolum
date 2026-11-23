package uy.edu.um.proytic2026.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RegistroWebController {

    @GetMapping("/registro")
    public String registro() {
        return "redirect:/registro/prestador";
    }

    @GetMapping("/registro/prestador")
    public String mostrarRegistroPrestador() {
        return "registro-prestador";
    }

    @GetMapping("/registro/empresa")
    public String mostrarRegistroEmpresa() {
        return "registro-empresa";
    }

    @GetMapping("/registro/particular")
    public String mostrarRegistroParticular() {
        return "registro-particular";
    }

    @GetMapping("/registro/turista")
    public String mostrarRegistroTurista() {
        return "registro-turista";
    }
}
