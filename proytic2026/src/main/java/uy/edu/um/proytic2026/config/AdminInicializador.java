package uy.edu.um.proytic2026.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import uy.edu.um.proytic2026.entities.Administrador;
import uy.edu.um.proytic2026.repository.AdministradorRepository;
import uy.edu.um.proytic2026.repository.UsuarioRepository;

@Component
public class AdminInicializador implements CommandLineRunner {

    private final AdministradorRepository administradorRepository;
    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${admin.inicial.nombre}")   private String nombre;
    @Value("${admin.inicial.apellido}") private String apellido;
    @Value("${admin.inicial.email}")    private String email;
    @Value("${admin.inicial.password}") private String password;

    public AdminInicializador(AdministradorRepository administradorRepository,
                              UsuarioRepository usuarioRepository) {
        this.administradorRepository = administradorRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public void run(String... args) {
        if (administradorRepository.count() > 0) {
            return; // ya hay admin, no hacemos nada
        }
        if (usuarioRepository.existsByUserEmail(email)) {
            throw new IllegalStateException("El email del admin inicial ya lo utiliza otro usuario: " + email);
        }

        Administrador admin = Administrador.builder()
                .name(nombre)
                .lastName(apellido)
                .userName("admin")
                .userEmail(email)
                .passwordHash(passwordEncoder.encode(password))
                .build();

        administradorRepository.save(admin);
        System.out.println(">> Admin inicial creado: " + email);
    }
}