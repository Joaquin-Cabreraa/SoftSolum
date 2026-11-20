package uy.edu.um.proytic2026.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.proytic2026.entities.Turista;
import uy.edu.um.proytic2026.services.TuristaService;

@RestController
@RequestMapping("/turistas")
public class TuristaController {

    @Autowired
    private TuristaService turistaService;

    // Recibe el formulario de registro-turista.html
    @PostMapping
    public ResponseEntity<String> registrarTurista(
            @RequestParam String nombre,
            @RequestParam(required = false) String apellido,
            @RequestParam(required = false) String documento,
            @RequestParam(required = false) String fechaNacimiento,
            @RequestParam String email,
            @RequestParam String telefono,
            @RequestParam String password,
            HttpServletRequest request) {

        // 1. Validar: si hay error, se lo devolvemos al formulario
        String error = turistaService.validarDatos(nombre, apellido, documento, fechaNacimiento, email, telefono, password);
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }

        // 2. Guardar
        Turista turista = turistaService.altaTurista(nombre, apellido, documento, fechaNacimiento, email, telefono, password);
        if (turista == null) {
            return ResponseEntity.badRequest()
                    .body("No fue posible completar el registro. Intentá de nuevo.");
        }

        // 3. Dejarlo logueado (CU06: el turista queda autenticado al registrarse).
        //    Guardamos lo mismo que guarda LoginController al iniciar sesión.
        HttpSession sesionAnterior = request.getSession(false);
        if (sesionAnterior != null) {
            sesionAnterior.invalidate();
        }
        HttpSession sesion = request.getSession(true);
        sesion.setAttribute("usuarioId", turista.getId());
        sesion.setAttribute("usuarioNombre", turista.getName());
        sesion.setAttribute("usuarioEmail", turista.getUserEmail());
        sesion.setAttribute("usuarioRol", "TURISTA");

        return ResponseEntity.ok("¡Cuenta creada! Bienvenido/a a UMnatural.");
    }
}