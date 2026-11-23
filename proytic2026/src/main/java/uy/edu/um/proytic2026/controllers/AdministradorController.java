package uy.edu.um.proytic2026.controllers;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import uy.edu.um.proytic2026.entities.EstadoEmprendimiento;
import uy.edu.um.proytic2026.services.AdministradorService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdministradorController {

    @Autowired
    private AdministradorService administradorService;

    // Consultar los emprendimientos pendientes.

    @GetMapping("/emprendimientos/pendientes")
    public List<Map<String, Object>> verPendientes() {
        return administradorService.listarPendientes();
    }

    // Aceptar un emprendimiento y registrar la verificación.

    @PutMapping("/emprendimientos/{id}/aceptar")
    public ResponseEntity<String> aceptar(
            @PathVariable Long id,
            HttpSession session) {

        Long administradorId = obtenerAdministradorId(session);

        if (administradorId == null) {
            return ResponseEntity.status(403)
                    .body("No se identificó al administrador.");
        }

        String error = administradorService.cambiarEstadoEmprendimiento(
                id,
                EstadoEmprendimiento.ACEPTADO,
                administradorId,
                null
        );

        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        return ResponseEntity.ok(
                "Emprendimiento aceptado. Verificación registrada."
        );
    }

    // Denegar un emprendimiento con motivo obligatorio.

    @PutMapping("/emprendimientos/{id}/denegar")
    public ResponseEntity<String> denegar(
            @PathVariable Long id,
            @RequestParam(required = false) String motivo,
            HttpSession session) {

        Long administradorId = obtenerAdministradorId(session);

        if (administradorId == null) {
            return ResponseEntity.status(403)
                    .body("No se identificó al administrador.");
        }

        String error = administradorService.cambiarEstadoEmprendimiento(
                id,
                EstadoEmprendimiento.DENEGADO,
                administradorId,
                motivo
        );

        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        return ResponseEntity.ok(
                "Emprendimiento denegado. Motivo e historial registrados."
        );
    }

    // Obtener el administrador autenticado desde la sesión.

    private Long obtenerAdministradorId(HttpSession session) {

        if (session == null) {
            return null;
        }

        Object rol = session.getAttribute("usuarioRol");
        Object id = session.getAttribute("usuarioId");

        if (!"ADMINISTRADOR".equals(rol)) {
            return null;
        }

        if (id instanceof Number numero) {
            return numero.longValue();
        }

        return null;
    }
}
