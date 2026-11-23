package uy.edu.um.proytic2026.controllers;
import jakarta.servlet.http.HttpSession;
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

    @PostMapping
    public ResponseEntity<?> crearEmprendimiento(
            @RequestBody Emprendimiento emprendimiento,
            HttpSession session) {

        Long prestadorId = (Long) session.getAttribute("usuarioId");

        if (prestadorId == null) {
            return ResponseEntity.status(401)
                    .body("No hay sesión iniciada.");
        }

        try {
            Emprendimiento nuevoEmprendimiento =
                    emprendimientoService.crearEmprendimiento(
                            emprendimiento, prestadorId);

            return ResponseEntity.ok(nuevoEmprendimiento);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }
}
