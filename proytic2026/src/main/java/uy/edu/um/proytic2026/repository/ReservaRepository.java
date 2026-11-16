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

    // Reservas pendientes de pago cuyo plazo de 2 horas empezó antes de "limite"
    // (el plazo corre desde plazoPagoDesde, o desde la creación en reservas viejas)
    @Query("SELECT r FROM Reserva r WHERE r.estado = :estado " +
            "AND COALESCE(r.plazoPagoDesde, r.fechaCreacion) < :limite")
    List<Reserva> pendientesConPlazoVencido(@Param("estado") EstadoReserva estado,
                                            @Param("limite") LocalDateTime limite);

    // Lista de espera de un horario, por orden de llegada
    @Query("SELECT r FROM Reserva r WHERE r.estado = :estado AND r.horario.actividad.id = :actividadId " +
            "AND r.horario.fecha = :fecha AND r.horario.horaInicio = :horaInicio ORDER BY r.fechaCreacion, r.id")
    List<Reserva> delHorarioEnEstado(@Param("estado") EstadoReserva estado,
                                     @Param("actividadId") Long actividadId,
                                     @Param("fecha") LocalDate fecha,
                                     @Param("horaInicio") LocalTime horaInicio);

    List<Reserva> findByEstado(EstadoReserva estado);

    // Reservas activas (no canceladas) de un horario: las que se cancelan si el prestador cancela la salida
    @Query("SELECT r FROM Reserva r WHERE r.estado <> :cancelada AND r.horario.actividad.id = :actividadId " +
            "AND r.horario.fecha = :fecha AND r.horario.horaInicio = :horaInicio")
    List<Reserva> delHorarioNoCanceladas(@Param("actividadId") Long actividadId,
                                         @Param("fecha") LocalDate fecha,
                                         @Param("horaInicio") LocalTime horaInicio,
                                         @Param("cancelada") EstadoReserva cancelada);

    java.util.Optional<Reserva> findByCodigo(String codigo);

    // ¿El turista ya tiene una reserva no cancelada en este horario? (no se reserva dos veces)
    @Query("SELECT COUNT(r) > 0 FROM Reserva r WHERE r.turista.id = :turistaId AND r.estado <> :cancelada " +
            "AND r.horario.actividad.id = :actividadId AND r.horario.fecha = :fecha AND r.horario.horaInicio = :horaInicio")
    boolean turistaTieneReservaEnHorario(@Param("turistaId") Long turistaId,
                                         @Param("actividadId") Long actividadId,
                                         @Param("fecha") LocalDate fecha,
                                         @Param("horaInicio") LocalTime horaInicio,
                                         @Param("cancelada") EstadoReserva cancelada);

    // Documentos de las personas que ya tienen lugar (o están en espera) en este horario,
    // para que la misma persona no ocupe dos lugares
    @Query("SELECT p.ci FROM Participante p WHERE p.reserva.estado <> :cancelada " +
            "AND p.reserva.horario.actividad.id = :actividadId AND p.reserva.horario.fecha = :fecha " +
            "AND p.reserva.horario.horaInicio = :horaInicio")
    List<String> documentosEnHorario(@Param("actividadId") Long actividadId,
                                     @Param("fecha") LocalDate fecha,
                                     @Param("horaInicio") LocalTime horaInicio,
                                     @Param("cancelada") EstadoReserva cancelada);

    // ¿Tiene reservas VIGENTES? (no canceladas y de salidas de hoy en adelante).
    // Son las que impiden editar la actividad: esos turistas reservaron con estas condiciones.
    @Query("SELECT COUNT(r) > 0 FROM Reserva r WHERE r.horario.actividad.id = :actividadId " +
            "AND r.estado <> :cancelada AND r.horario.fecha >= :hoy")
    boolean actividadTieneReservasVigentes(@Param("actividadId") Long actividadId,
                                           @Param("cancelada") EstadoReserva cancelada,
                                           @Param("hoy") LocalDate hoy);

    // ¿La actividad tiene alguna reserva, en cualquiera de sus horarios? (RNE #15)
    @Query("SELECT COUNT(r) > 0 FROM Reserva r WHERE r.horario.actividad.id = :actividadId")
    boolean actividadTieneReservas(@Param("actividadId") Long actividadId);

    // ¿Este horario tiene reservas que NO estén canceladas?
    @Query("SELECT COUNT(r) > 0 FROM Reserva r WHERE r.estado <> :cancelada AND r.horario.actividad.id = :actividadId " +
            "AND r.horario.fecha = :fecha AND r.horario.horaInicio = :horaInicio")
    boolean horarioTieneReservasActivas(@Param("actividadId") Long actividadId,
                                        @Param("fecha") LocalDate fecha,
                                        @Param("horaInicio") LocalTime horaInicio,
                                        @Param("cancelada") EstadoReserva cancelada);

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
