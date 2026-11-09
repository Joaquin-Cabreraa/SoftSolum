package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entities.Actividad;

import java.util.List;

public interface ActividadRepository extends JpaRepository<Actividad, Long> {

    // Actividades de un emprendimiento, las más nuevas primero
    List<Actividad> findByEmprendimientoIdOrderByIdDesc(Long emprendimientoId);

    // ¿Alguna actividad usa este tipo de actividad / equipamiento?
    // (para no dejar borrar un tipo que está en uso)
    boolean existsByTipoActividad_Id(Long tipoActividadId);

    boolean existsByTiposEquipamiento_Id(Long tipoEquipamientoId);
}
