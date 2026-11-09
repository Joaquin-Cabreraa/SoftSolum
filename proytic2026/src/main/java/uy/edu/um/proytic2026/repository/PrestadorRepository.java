package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entities.Emprendimiento;
import uy.edu.um.proytic2026.entities.Prestador;

import java.util.Optional;

public interface PrestadorRepository extends JpaRepository<Prestador, Long> {

    // Busca el prestador (empresa o particular) dueño de ese emprendimiento
    Optional<Prestador> findByEmprendimiento(Emprendimiento emprendimiento);
}