package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entities.Turista;

public interface TuristaRepository extends JpaRepository<Turista, Long> {
    // Con esto Spring ya nos da save, findById, findAll, delete, etc.

    // ¿Ya hay un turista con este documento? (una persona = una cuenta)
    boolean existsByDocumentoIgnoreCase(String documento);

    // Igual, pero sin contar al propio turista (cuando edita sus datos)
    boolean existsByDocumentoIgnoreCaseAndIdNot(String documento, Long id);
}