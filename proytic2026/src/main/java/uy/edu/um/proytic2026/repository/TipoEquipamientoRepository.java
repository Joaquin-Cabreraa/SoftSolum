package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entities.TipoEquipamiento;

import java.util.List;

public interface TipoEquipamientoRepository extends JpaRepository<TipoEquipamiento, Long> {

    List<TipoEquipamiento> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    java.util.Optional<TipoEquipamiento> findFirstByNombreIgnoreCase(String nombre);
}
