package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entities.Turista;

public interface TuristaRepository extends JpaRepository<Turista, Long> {
    // Con esto Spring ya nos da save, findById, findAll, delete, etc.
}