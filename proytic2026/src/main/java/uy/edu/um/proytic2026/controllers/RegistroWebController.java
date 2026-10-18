package uy.edu.um.proytic2026.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RegistroWebController {

    @GetMapping("/registro/prestador")
    public String mostrarRegistroPrestador() {
        return "registro-prestador";
    }
}
