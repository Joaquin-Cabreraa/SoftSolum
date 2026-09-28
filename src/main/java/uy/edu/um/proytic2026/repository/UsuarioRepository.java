package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entities.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>{//Con esto Spring ya crea la clase con todas las operaciones{

    public boolean existsByUserName(String userName);//poner existsBy, Spring ya crea una consults select * from where para ver si ya existe algo

    public boolean existsByUserEmail(String userEmail);


}