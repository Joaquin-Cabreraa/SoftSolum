package uy.edu.um.proytic2026.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.proytic2026.services.PrestadorService;

@RestController
@RequestMapping("/prestadores")
// Todas las rutas empiezan con /prestadores.

public class PrestadorController {

    @Autowired
    private PrestadorService prestadorService;

    @PostMapping("/empresa")
    // Pedido POST a /prestadores/empresa. (POST = "quiero crear algo nuevo".)

    public ResponseEntity<String> registrarEmpresa(@RequestParam String razonSocial,
                                                   @RequestParam String rut,
                                                   @RequestParam String email,
                                                   @RequestParam(required = false) String telefono,
                                                   @RequestParam String password,
                                                   @RequestParam String nombreEmprendimiento,
                                                   @RequestParam String direccion) {
        // @RequestParam: "este dato viene en el pedido con este nombre".
        // required = false: el teléfono es opcional.

        if (prestadorService.altaEmpresa(razonSocial, rut, email, telefono, password, nombreEmprendimiento, direccion)) {
            return ResponseEntity.ok("Empresa registrada. Emprendimiento pendiente de verificación.");
        }
        return ResponseEntity.badRequest().body("No se pudo registrar: faltan datos o el email/RUT ya existe.");
    }

    @PostMapping("/particular")
    public ResponseEntity<String> registrarParticular(@RequestParam String nombre,
                                                      @RequestParam String apellido,
                                                      @RequestParam String documento,
                                                      @RequestParam String email,
                                                      @RequestParam(required = false) String telefono,
                                                      @RequestParam String password,
                                                      @RequestParam String nombreEmprendimiento,
                                                      @RequestParam String direccion) {

        if (prestadorService.altaParticular(nombre, apellido, documento, email, telefono, password, nombreEmprendimiento, direccion)) {
            return ResponseEntity.ok("Particular registrado. Emprendimiento pendiente de verificación.");
        }
        return ResponseEntity.badRequest().body("No se pudo registrar: faltan datos o el email/CI ya existe.");
    }
}