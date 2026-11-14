package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import uy.edu.um.proytic2026.entities.Turista;
import uy.edu.um.proytic2026.repository.TuristaRepository;
import uy.edu.um.proytic2026.repository.UsuarioRepository;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Service
public class TuristaService {

    @Autowired
    private TuristaRepository turistaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Revisa los datos. Devuelve el mensaje de error, o null si está todo bien.
    public String validarDatos(String nombre, String apellido, String documento, String fechaNacimiento,
                               String email, String telefono, String password) {

        if (vacio(nombre) || vacio(apellido) || vacio(documento) || vacio(fechaNacimiento)
                || vacio(email) || vacio(telefono) || vacio(password)) {
            return "Completá todos los campos obligatorios.";
        }

        // CI uruguaya o pasaporte: letras y números, sin puntos ni guiones
        if (!limpiarDocumento(documento).matches("[0-9A-Za-z]{5,20}")) {
            return "El documento no es válido.";
        }

        LocalDate nacimiento = convertirFecha(fechaNacimiento);
        if (nacimiento == null || !nacimiento.isBefore(LocalDate.now())) {
            return "La fecha de nacimiento no es válida.";
        }

        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            return "El correo electrónico no tiene un formato válido.";
        }

        if (!telefono.matches("09[1-9][0-9]{6}")) {
            return "Ingresá un celular válido, por ejemplo 099123456.";
        }

        if (password.length() < 8) {
            return "La contraseña debe tener al menos 8 caracteres.";
        }

        if (usuarioRepository.existsByUserEmailIgnoreCase(email.trim())) {
            return "Ya existe una cuenta registrada con ese correo electrónico.";
        }

        return null;   // todo correcto
    }

    // Crea y guarda el turista. Devuelve el turista guardado (con su id),
    // o null si los datos no son válidos.
    public Turista altaTurista(String nombre, String apellido, String documento, String fechaNacimiento,
                               String email, String telefono, String password) {

        if (validarDatos(nombre, apellido, documento, fechaNacimiento, email, telefono, password) != null) {
            return null;
        }

        Turista turista = new Turista();
        turista.setName(nombre.trim());
        turista.setLastName(apellido.trim());
        turista.setDocumento(limpiarDocumento(documento));
        turista.setFechaNacimiento(convertirFecha(fechaNacimiento));
        turista.setUserName(email.trim().toLowerCase());
        turista.setUserEmail(email.trim().toLowerCase());
        turista.setTelefono(telefono);
        turista.setPasswordHash(passwordEncoder.encode(password));   // nunca se guarda en texto plano

        return turistaRepository.save(turista);
    }

    private boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    // Saca puntos, guiones y espacios: "1.234.567-2" -> "12345672"
    private String limpiarDocumento(String documento) {
        return documento.replaceAll("[.\\-\\s]", "");
    }

    // "2001-05-30" (formato del <input type="date">) -> LocalDate, o null si no es válida
    private LocalDate convertirFecha(String texto) {
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}