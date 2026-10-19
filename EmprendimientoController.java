package uy.edu.um.proytic2026.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.proytic2026.entities.Emprendimiento;
import uy.edu.um.proytic2026.services.EmprendimientoService;

@RestController
@RequestMapping("/emprendimientos")
public class EmprendimientoController {

    @Autowired
    private EmprendimientoService emprendimientoService;

    // registra un nuevo emprendimiento
    @PostMapping
    public ResponseEntity<Emprendimiento> crearEmprendimiento(
            @RequestBody Emprendimiento emprendimiento) {

        Emprendimiento nuevoEmprendimiento =
                emprendimientoService.crearEmprendimiento(emprendimiento);

        return ResponseEntity.ok(nuevoEmprendimiento);
    }
}