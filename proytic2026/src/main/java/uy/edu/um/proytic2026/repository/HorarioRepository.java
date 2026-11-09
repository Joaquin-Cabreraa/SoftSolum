package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.proytic2026.entities.Horario;
import uy.edu.um.proytic2026.entities.HorarioId;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface HorarioRepository extends JpaRepository<Horario, HorarioId> {

    // Horarios de una actividad desde cierta fecha, ordenados por fecha y hora
    List<Horario> findByActividadIdAndFechaGreaterThanEqualOrderByFechaAscHoraInicioAsc(Long actividadId, LocalDate desde);

    // Descuenta cupos de forma ATÓMICA (RNE #3): la resta y el control
    // "¿quedan suficientes?" se hacen en una sola sentencia UPDATE en la base.
    // Si dos turistas reservan a la vez los últimos cupos, la base procesa
    // un UPDATE después del otro: al segundo ya no le cumple la condición y
    // devuelve 0 filas modificadas. Así los cupos nunca quedan negativos (RNE #4).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Horario h SET h.cuposDisponibles = h.cuposDisponibles - :cantidad " +
            "WHERE h.actividad.id = :actividadId AND h.fecha = :fecha AND h.horaInicio = :horaInicio " +
            "AND h.cuposDisponibles >= :cantidad")
    int descontarCupos(@Param("actividadId") Long actividadId,
                       @Param("fecha") LocalDate fecha,
                       @Param("horaInicio") LocalTime horaInicio,
                       @Param("cantidad") int cantidad);

    // Devuelve cupos (cuando se cancela una reserva). Nunca supera los cupos totales.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Horario h SET h.cuposDisponibles = h.cuposDisponibles + :cantidad " +
            "WHERE h.actividad.id = :actividadId AND h.fecha = :fecha AND h.horaInicio = :horaInicio " +
            "AND h.cuposDisponibles + :cantidad <= h.cuposTotales")
    int devolverCupos(@Param("actividadId") Long actividadId,
                      @Param("fecha") LocalDate fecha,
                      @Param("horaInicio") LocalTime horaInicio,
                      @Param("cantidad") int cantidad);
}
