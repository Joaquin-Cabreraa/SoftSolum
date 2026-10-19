package uy.edu.um.proytic2026.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Muestra las pantallas principales (las páginas HTML de la carpeta templates)
@Controller
public class PantallasWebController {

    @GetMapping("/")
    public String inicio() {
        return "inicio";               // templates/inicio.html
    }

    @GetMapping("/panel/prestador")
    public String panelPrestador() {
        return "panel-prestador";      // templates/panel-prestador.html
    }

    @GetMapping("/panel/admin")
    public String panelAdmin() {
        return "panel-admin";          // templates/panel-admin.html
    }
}
