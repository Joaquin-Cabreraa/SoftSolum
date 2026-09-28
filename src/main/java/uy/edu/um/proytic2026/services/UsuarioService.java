package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uy.edu.um.proytic2026.entities.Usuario;
import uy.edu.um.proytic2026.repository.UsuarioRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public boolean altaUsuario(String nombre, String apellido, String email, String telefono, String password) {
        if (nombre==null || apellido==null || email == null){
            return false;
        }
        if (password == null || password.isBlank()) {
            return false;
        }
        if (usuarioRepository.existsByUserEmail(email)) {
            return false;
        }

        int i = 1;

        String userName = nombre + "." + apellido;
        while(usuarioRepository.existsByUserName(userName)){
            userName = userName + i++;
        }

        Usuario nuevoUsuario = Usuario.builder()
                .name(nombre)
                .lastName(apellido)
                .userName(userName)
                .userEmail(email)
                .telefono(telefono)
                .passwordHash(passwordEncoder.encode(password)) //calcula el hash de password y lo coloca en el atributo passwordHash !!!
                .build();

        nuevoUsuario =  usuarioRepository.save(nuevoUsuario);

        return nuevoUsuario.getId() > 0L;
    }
}
