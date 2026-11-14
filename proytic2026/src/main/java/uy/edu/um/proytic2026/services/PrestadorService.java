
package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.repository.*;

@Service
public class PrestadorService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private ParticularRepository particularRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ---------- Registro de EMPRESA ----------

    public boolean altaEmpresa(String razonSocial, String rut, String email, String telefono,
                               String password, String nombreEmprendimiento, String direccion,
                               String descripcion) {

        // Comprobar campos obligatorios
        if (vacio(razonSocial) || vacio(rut) || vacio(email)
                || vacio(password) || vacio(nombreEmprendimiento)) {
            return false;
        }

        // Comprobar que el email no esté registrado
        if (usuarioRepository.existsByUserEmailIgnoreCase(email.trim())) {
            return false;
        }

        // Comprobar que el RUT no esté registrado
        if (empresaRepository.existsByRut(rut)) {
            return false;
        }

        // Validar teléfono (opcional)
        if (!telefonoValido(telefono)) {
            return false;
        }

        // Validar RUT y dígito verificador
        if (!rutValido(rut)) {
            return false;
        }

        Empresa empresa = new Empresa();

        empresa.setName(razonSocial);
        empresa.setRut(rut);
        empresa.setUserName(email.trim().toLowerCase());
        empresa.setUserEmail(email.trim().toLowerCase());
        empresa.setTelefono(telefono);
        empresa.setPasswordHash(passwordEncoder.encode(password));

        empresa.setEmprendimiento(
                crearEmprendimiento(nombreEmprendimiento, direccion, email, telefono, descripcion)
        );

        empresaRepository.save(empresa);

        return true;
    }

    // ---------- Registro de PARTICULAR ----------

    public boolean altaParticular(String nombre, String apellido, String documento, String email,
                                  String telefono, String password, String nombreEmprendimiento, String direccion,
                               String descripcion) {

        // Comprobar campos obligatorios
        if (vacio(nombre) || vacio(apellido) || vacio(documento)
                || vacio(email) || vacio(password)
                || vacio(nombreEmprendimiento)) {
            return false;
        }

        // Comprobar que el email no esté registrado
        if (usuarioRepository.existsByUserEmailIgnoreCase(email.trim())) {
            return false;
        }

        // Comprobar que la cédula no esté registrada
        if (particularRepository.existsByDocumento(documento)) {
            return false;
        }

        // Validar teléfono (opcional)
        if (!telefonoValido(telefono)) {
            return false;
        }

        // Validar cédula y dígito verificador
        if (!cedulaValida(documento)) {
            return false;
        }

        Particular particular = new Particular();

        particular.setName(nombre);
        particular.setLastName(apellido);
        particular.setDocumento(documento);
        particular.setUserName(email.trim().toLowerCase());
        particular.setUserEmail(email.trim().toLowerCase());
        particular.setTelefono(telefono);
        particular.setPasswordHash(passwordEncoder.encode(password));

        particular.setEmprendimiento(
                crearEmprendimiento(nombreEmprendimiento, direccion, email, telefono, descripcion)
        );

        particularRepository.save(particular);

        return true;
    }

    // ---------- Métodos de ayuda ----------

    // Crea el emprendimiento con sus datos de contacto (el enunciado pide
    // "datos de identificación, contacto, ubicación y una descripción general").
    private Emprendimiento crearEmprendimiento(String nombre, String direccion, String email,
                                               String telefono, String descripcion) {
        Emprendimiento emp = new Emprendimiento();

        emp.setNombre(nombre);
        emp.setUbicacion(direccion);
        emp.setEmail(email.trim().toLowerCase());
        emp.setTelefono(vacio(telefono) ? null : telefono.trim());
        emp.setDescripcion(vacio(descripcion) ? null : descripcion.trim());
        emp.setEstado(EstadoEmprendimiento.PENDIENTE);

        return emp;
    }

    private boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    // ---------- Validación de TELÉFONO ----------

    private boolean telefonoValido(String telefono) {

        // El teléfono es opcional
        if (telefono == null || telefono.isBlank()) {
            return true;
        }

        // Celular uruguayo de 9 dígitos
        return telefono.matches("09[1-9][0-9]{6}");
    }

    // ---------- Validación de CÉDULA ----------

    private boolean cedulaValida(String cedula) {

        if (cedula == null || !cedula.matches("[0-9]{7,8}")) {
            return false;
        }

        // Si tiene 7 dígitos, agregamos un cero al principio
        cedula = String.format("%8s", cedula).replace(' ', '0');

        int[] multiplicadores = {2, 9, 8, 7, 6, 3, 4};

        int suma = 0;

        for (int i = 0; i < 7; i++) {
            int digito = Character.getNumericValue(cedula.charAt(i));
            suma += digito * multiplicadores[i];
        }

        int verificador = (10 - (suma % 10)) % 10;

        int ultimoDigito = Character.getNumericValue(cedula.charAt(7));

        return verificador == ultimoDigito;
    }

    // ---------- Validación de RUT ----------

    private boolean rutValido(String rut) {

        // El RUT debe tener exactamente 12 números
        if (rut == null || !rut.matches("[0-9]{12}")) {
            return false;
        }

        // Multiplicadores para el cálculo del dígito verificador
        int[] multiplicadores = {4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        int suma = 0;

        for (int i = 0; i < 11; i++) {
            int digito = Character.getNumericValue(rut.charAt(i));
            suma += digito * multiplicadores[i];
        }

        int resto = suma % 11;
        int verificador = (11 - resto) % 11;

        // Si da 10, no corresponde a un dígito decimal válido
        if (verificador == 10) {
            return false;
        }

        int ultimoDigito = Character.getNumericValue(rut.charAt(11));

        return verificador == ultimoDigito;
    }
}
