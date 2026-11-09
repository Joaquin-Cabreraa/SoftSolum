package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entities.TipoActividad;

import java.util.List;

public interface TipoActividadRepository extends JpaRepository<TipoActividad, Long> {

    List<TipoActividad> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    java.util.Optional<TipoActividad> findFirstByNombreIgnoreCase(String nombre);
}
