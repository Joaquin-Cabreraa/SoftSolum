package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import uy.edu.um.proytic2026.entities.Turista;
import uy.edu.um.proytic2026.repository.TuristaRepository;
import uy.edu.um.proytic2026.repository.UsuarioRepository;

@Service
public class TuristaService {

    @Autowired
    private TuristaRepository turistaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Revisa los datos. Devuelve el mensaje de error, o null si está todo bien.
    public String validarDatos(String nombre, String email, String telefono, String password) {

        if (vacio(nombre) || vacio(email) || vacio(telefono) || vacio(password)) {
            return "Completá todos los campos obligatorios.";
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
    public Turista altaTurista(String nombre, String email, String telefono, String password) {

        if (validarDatos(nombre, email, telefono, password) != null) {
            return null;
        }

        Turista turista = new Turista();
        turista.setName(nombre);
        turista.setUserName(email.trim().toLowerCase());
        turista.setUserEmail(email.trim().toLowerCase());
        turista.setTelefono(telefono);
        turista.setPasswordHash(passwordEncoder.encode(password));   // nunca se guarda en texto plano

        return turistaRepository.save(turista);
    }

    private boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}