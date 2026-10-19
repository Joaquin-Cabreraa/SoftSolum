package uy.edu.um.proytic2026.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import uy.edu.um.proytic2026.repository.EmprendimientoRepository;

@Controller
public class RegistroWebController {

    private final EmprendimientoRepository emprendimientoRepository;

    public RegistroWebController(EmprendimientoRepository emprendimientoRepository) {
        this.emprendimientoRepository = emprendimientoRepository;
    }

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

    @GetMapping("/alta/usuario")
    public String altaUsuario() {
        return "altaUsuario";
    }

    @GetMapping("/alta/emprendimiento")
    public String altaEmprendimiento() {
        return "altaEmprendimiento";
    }

    @GetMapping("/alta/actividad")
    public String altaActividad(Model model) {
        model.addAttribute("emprendimientos", emprendimientoRepository.findAll());
        return "altaActividad";
    }
}
