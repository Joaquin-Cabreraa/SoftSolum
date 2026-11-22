package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.proytic2026.entities.Resena;

import java.time.LocalDateTime;
import java.util.List;

public interface ResenaRepository extends JpaRepository<Resena, Long> {

    boolean existsByReservaId(Long reservaId);

    java.util.Optional<Resena> findByReservaId(Long reservaId);

    // Las que se ven en el portal (no ocultas), de la más nueva a la más vieja
    @Query("SELECT r FROM Resena r WHERE r.actividad.id = :actividadId AND (r.oculta IS NULL OR r.oculta = false) ORDER BY r.fecha DESC")
    List<Resena> visiblesDeActividad(@Param("actividadId") Long actividadId);

    // Promedio y cantidad por actividad (solo visibles): filas [actividadId, promedio, cantidad]
    @Query("SELECT r.actividad.id, AVG(r.puntaje), COUNT(r) FROM Resena r WHERE (r.oculta IS NULL OR r.oculta = false) GROUP BY r.actividad.id")
    List<Object[]> promedios();

    // Todas las de las actividades de un emprendimiento (para que el prestador responda)
    @Query("SELECT r FROM Resena r WHERE r.actividad.emprendimiento.id = :emprendimientoId ORDER BY r.fecha DESC")
    List<Resena> delEmprendimiento(@Param("emprendimientoId") Long emprendimientoId);

    List<Resena> findAllByOrderByFechaDesc();

    // Promedio y cantidad por actividad de las valoraciones visibles hechas entre dos momentos
    // (desde incluido, hasta excluido): filas [actividadId, promedio, cantidad]. La usa la API publica (top del mes).
    @Query("SELECT r.actividad.id, AVG(r.puntaje), COUNT(r) FROM Resena r " +
            "WHERE (r.oculta IS NULL OR r.oculta = false) AND r.fecha >= :desde AND r.fecha < :hasta " +
            "GROUP BY r.actividad.id")
    List<Object[]> promediosEntre(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
