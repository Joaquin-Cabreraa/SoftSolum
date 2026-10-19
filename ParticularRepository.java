package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entities.Particular;

public interface ParticularRepository extends JpaRepository<Particular, Long> {
    boolean existsByDocumento(String documento);
    // "¿Existe algún particular con esta CI?"
}