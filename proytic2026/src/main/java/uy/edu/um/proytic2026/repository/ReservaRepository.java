package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.proytic2026.entities.EstadoReserva;
import uy.edu.um.proytic2026.entities.Reserva;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    // "Mis reservas": las del turista, de la más nueva a la más vieja
    List<Reserva> findByTuristaIdOrderByFechaCreacionDesc(Long turistaId);

    // Reservas en cierto estado creadas antes de cierto momento
    // (se usa para encontrar las pendientes de pago con más de 2 horas)
    List<Reserva> findByEstadoAndFechaCreacionBefore(EstadoReserva estado, LocalDateTime limite);

    // ¿La actividad tiene alguna reserva, en cualquiera de sus horarios? (RNE #15)
    @Query("SELECT COUNT(r) > 0 FROM Reserva r WHERE r.horario.actividad.id = :actividadId")
    boolean actividadTieneReservas(@Param("actividadId") Long actividadId);

    // ¿Este horario tiene alguna reserva? (RNE #6)
    @Query("SELECT COUNT(r) > 0 FROM Reserva r WHERE r.horario.actividad.id = :actividadId " +
            "AND r.horario.fecha = :fecha AND r.horario.horaInicio = :horaInicio")
    boolean horarioTieneReservas(@Param("actividadId") Long actividadId,
                                 @Param("fecha") LocalDate fecha,
                                 @Param("horaInicio") LocalTime horaInicio);

    // Reservas de todas las actividades de un emprendimiento (CU-P11),
    // ordenadas por fecha de la actividad
    @Query("SELECT r FROM Reserva r WHERE r.horario.actividad.emprendimiento.id = :emprendimientoId " +
            "ORDER BY r.horario.fecha, r.horario.horaInicio, r.fechaCreacion")
    List<Reserva> reservasDelEmprendimiento(@Param("emprendimientoId") Long emprendimientoId);
}
