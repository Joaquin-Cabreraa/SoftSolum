package uy.edu.um.proytic2026.controllers;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.proytic2026.entities.Emprendimiento;
import uy.edu.um.proytic2026.services.AdministradorService;
import java.util.List;


@RestController
@RequestMapping("/admin")

public class AdministradorController {

    @Autowired
    private AdministradorService administradorService;

    @GetMapping("/emprendimientos/pendientes")

    public List<Emprendimiento> verPendientes() {
        return administradorService.listarPendientes();

    }

    @PutMapping("/emprendimientos/{id}/aceptar")


    public ResponseEntity<String> aceptar(@PathVariable Long id) {

        if (administradorService.aceptarEmprendimiento(id)) {
            return ResponseEntity.ok("Emprendimiento aceptado");
        }
        return ResponseEntity.badRequest().body("No se pudo aceptar: no existe o no está pendiente");
    }

    @PutMapping("/emprendimientos/{id}/denegar")

    public ResponseEntity<String> denegar(@PathVariable Long id) {
        if (administradorService.denegarEmprendimiento(id)) {
            return ResponseEntity.ok("Emprendimiento denegado");
        }
        return ResponseEntity.badRequest().body("No se pudo denegar: no existe o no está pendiente");
    }
}