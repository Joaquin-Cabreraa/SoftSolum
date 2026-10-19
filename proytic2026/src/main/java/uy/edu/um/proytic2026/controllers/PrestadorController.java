
package uy.edu.um.proytic2026.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.proytic2026.services.PrestadorService;
import uy.edu.um.proytic2026.repository.UsuarioRepository;
import uy.edu.um.proytic2026.repository.EmpresaRepository;
import uy.edu.um.proytic2026.repository.ParticularRepository;

@RestController
@RequestMapping("/prestadores")
public class PrestadorController {

    @Autowired
    private PrestadorService prestadorService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private ParticularRepository particularRepository;

    // ---------- Registro de EMPRESA ----------

    @PostMapping("/empresa")
    public ResponseEntity<String> registrarEmpresa(
            @RequestParam String razonSocial,
            @RequestParam String rut,
            @RequestParam String email,
            @RequestParam(required = false) String telefono,
            @RequestParam String password,
            @RequestParam String nombreEmprendimiento,
            @RequestParam String direccion) {

        // Comprobar campos obligatorios
        if (vacio(razonSocial) || vacio(rut) || vacio(email)
                || vacio(password) || vacio(nombreEmprendimiento)) {
            return ResponseEntity.badRequest()
                    .body("Completá todos los campos obligatorios.");
        }

        // Validar teléfono
        if (!telefonoValido(telefono)) {
            return ResponseEntity.badRequest()
                    .body("Ingresá un celular válido, por ejemplo 099123456.");
        }

        // Validar RUT
        if (!rutValido(rut)) {
            return ResponseEntity.badRequest()
                    .body("El RUT ingresado no es válido. Revisá sus 12 dígitos.");
        }

        // Verificar email repetido
        if (usuarioRepository.existsByUserEmail(email)) {
            return ResponseEntity.badRequest()
                    .body("Ya existe una cuenta registrada con ese correo electrónico.");
        }

        // Verificar RUT repetido
        if (empresaRepository.existsByRut(rut)) {
            return ResponseEntity.badRequest()
                    .body("Ya existe una empresa registrada con ese RUT.");
        }

        // Registrar empresa
        if (prestadorService.altaEmpresa(
                razonSocial, rut, email, telefono, password,
                nombreEmprendimiento, direccion)) {

            return ResponseEntity.ok(
                    "Empresa registrada. Emprendimiento pendiente de verificación.");
        }

        return ResponseEntity.badRequest()
                .body("No se pudo registrar la empresa. Revisá los datos ingresados.");
    }

    // ---------- Registro de PARTICULAR ----------

    @PostMapping("/particular")
    public ResponseEntity<String> registrarParticular(
            @RequestParam String nombre,
            @RequestParam String apellido,
            @RequestParam String documento,
            @RequestParam String email,
            @RequestParam(required = false) String telefono,
            @RequestParam String password,
            @RequestParam String nombreEmprendimiento,
            @RequestParam String direccion) {

        // Comprobar campos obligatorios
        if (vacio(nombre) || vacio(apellido) || vacio(documento)
                || vacio(email) || vacio(password)
                || vacio(nombreEmprendimiento)) {
            return ResponseEntity.badRequest()
                    .body("Completá todos los campos obligatorios.");
        }

        // Validar teléfono
        if (!telefonoValido(telefono)) {
            return ResponseEntity.badRequest()
                    .body("Ingresá un celular válido, por ejemplo 099123456.");
        }

        // Validar cédula
        if (!cedulaValida(documento)) {
            return ResponseEntity.badRequest()
                    .body("La cédula ingresada no es válida. Revisá el número y su dígito verificador.");
        }

        // Verificar email repetido
        if (usuarioRepository.existsByUserEmail(email)) {
            return ResponseEntity.badRequest()
                    .body("Ya existe una cuenta registrada con ese correo electrónico.");
        }

        // Verificar cédula repetida
        if (particularRepository.existsByDocumento(documento)) {
            return ResponseEntity.badRequest()
                    .body("Ya existe un prestador registrado con esa cédula.");
        }

        // Registrar particular
        if (prestadorService.altaParticular(
                nombre, apellido, documento, email, telefono,
                password, nombreEmprendimiento, direccion)) {

            return ResponseEntity.ok(
                    "Particular registrado. Emprendimiento pendiente de verificación.");
        }

        return ResponseEntity.badRequest()
                .body("No se pudo registrar el particular. Revisá los datos ingresados.");
    }

    // ---------- Métodos de validación ----------

    private boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private boolean telefonoValido(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return true;
        }

        return telefono.matches("09[1-9][0-9]{6}");
    }

    private boolean cedulaValida(String cedula) {
        if (cedula == null || !cedula.matches("[0-9]{7,8}")) {
            return false;
        }

        cedula = String.format("%8s", cedula).replace(' ', '0');

        int[] multiplicadores = {2, 9, 8, 7, 6, 3, 4};
        int suma = 0;

        for (int i = 0; i < 7; i++) {
            suma += Character.getNumericValue(cedula.charAt(i))
                    * multiplicadores[i];
        }

        int verificador = (10 - (suma % 10)) % 10;

        return verificador ==
                Character.getNumericValue(cedula.charAt(7));
    }

    private boolean rutValido(String rut) {
        if (rut == null || !rut.matches("[0-9]{12}")) {
            return false;
        }

        int[] multiplicadores = {4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int suma = 0;

        for (int i = 0; i < 11; i++) {
            suma += Character.getNumericValue(rut.charAt(i))
                    * multiplicadores[i];
        }

        int verificador = (11 - (suma % 11)) % 11;

        if (verificador == 10) {
            return false;
        }

        return verificador ==
                Character.getNumericValue(rut.charAt(11));
    }
}
