package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entities.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>{//Con esto Spring ya crea la clase con todas las operaciones{

    public boolean existsByUserName(String userName);//poner existsBy, Spring ya crea una consults select * from where para ver si ya existe algo

    public boolean existsByUserEmail(String userEmail);

    public java.util.Optional<Usuario> findByUserEmail(String userEmail);

    // Igual que los de arriba pero sin distinguir mayúsculas/minúsculas
    // ("Ana@Mail.com" y "ana@mail.com" son el mismo correo)
    public boolean existsByUserEmailIgnoreCase(String userEmail);

    public java.util.Optional<Usuario> findByUserEmailIgnoreCase(String userEmail);
}