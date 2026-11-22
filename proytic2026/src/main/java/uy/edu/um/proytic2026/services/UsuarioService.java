package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.entities.Usuario;
import uy.edu.um.proytic2026.repository.UsuarioRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final int MAX_INTENTOS = 5;
    private static final int MINUTOS_BLOQUEO = 15;

    // LOGIN: verifica la contraseña y controla los intentos fallidos.
    @Transactional
    public Optional<Usuario> autenticar(String email, String password) {

        if (email == null || email.isBlank()
                || password == null || password.isBlank()) {
            return Optional.empty();
        }

        Optional<Usuario> encontrado =
                usuarioRepository.findByUserEmailIgnoreCase(email.trim());

        if (encontrado.isEmpty()) {
            return Optional.empty();
        }

        Usuario usuario = encontrado.get();
        LocalDateTime ahora = LocalDateTime.now();

        // Si la cuenta sigue bloqueada, no permite ingresar.
        if (usuario.getBloqueadoHasta() != null) {

            if (ahora.isBefore(usuario.getBloqueadoHasta())) {
                return Optional.empty();
            }

            // Terminó el bloqueo: reiniciamos el contador.
            usuario.setBloqueadoHasta(null);
            usuario.setIntentosFallidos(0);
        }

        // Contraseña incorrecta.
        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {

            int intentos = usuario.getIntentosFallidos() + 1;
            usuario.setIntentosFallidos(intentos);

            if (intentos >= MAX_INTENTOS) {
                usuario.setBloqueadoHasta(
                        ahora.plusMinutes(MINUTOS_BLOQUEO)
                );
            }

            usuarioRepository.save(usuario);
            return Optional.empty();
        }

        // Contraseña correcta: reiniciamos los intentos.
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);

        usuarioRepository.save(usuario);

        return Optional.of(usuario);
    }

    // Comprueba si una cuenta está bloqueada actualmente.
    @Transactional(readOnly = true)
    public boolean estaBloqueado(String email) {

        if (email == null || email.isBlank()) {
            return false;
        }

        return usuarioRepository.findByUserEmailIgnoreCase(email.trim())
                .map(usuario ->
                        usuario.getBloqueadoHasta() != null
                                && LocalDateTime.now().isBefore(usuario.getBloqueadoHasta())
                )
                .orElse(false);
    }

    // REGISTRO: crea un usuario nuevo.
    public boolean altaUsuario(String nombre, String apellido, String email,
                               String telefono, String password) {

        if (nombre == null || apellido == null || email == null) {
            return false;
        }

        if (password == null || password.isBlank()) {
            return false;
        }

        if (usuarioRepository.existsByUserEmailIgnoreCase(email.trim())) {
            return false;
        }

        int i = 1;
        String userName = nombre + "." + apellido;

        while (usuarioRepository.existsByUserName(userName)) {
            userName = nombre + "." + apellido + i++;
        }

        Usuario nuevoUsuario = Usuario.builder()
                .name(nombre)
                .lastName(apellido)
                .userName(userName)
                .userEmail(email)
                .telefono(telefono)
                .passwordHash(passwordEncoder.encode(password))
                .build();

        nuevoUsuario = usuarioRepository.save(nuevoUsuario);

        return nuevoUsuario.getId() != null && nuevoUsuario.getId() > 0L;
    }
}