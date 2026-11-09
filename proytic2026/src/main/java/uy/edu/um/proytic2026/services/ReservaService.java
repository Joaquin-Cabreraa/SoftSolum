package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.repository.HorarioRepository;
import uy.edu.um.proytic2026.repository.ReservaRepository;
import uy.edu.um.proytic2026.repository.TuristaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Reservas de los turistas.
// CU-T06 Reservar actividad, CU-T09 Mis reservas, CU-S01 Liberar cupo por vencimiento.
@Service
public class ReservaService {

    // Plazo para pagar una reserva antes de que se cancele sola (RNE #2, acta P10-P11)
    public static final int HORAS_PARA_PAGAR = 2;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private HorarioRepository horarioRepository;

    @Autowired
    private TuristaRepository turistaRepository;

    @Autowired
    private CatalogoService catalogoService;

    // ---------- Validación ----------

    // Revisa todos los datos de la reserva. Devuelve el mensaje de error,
    // o null si está todo bien. (Mismo estilo que TuristaService.validarDatos)
    // Las listas traen un dato por participante, en el mismo orden.
    public String validarReserva(Long turistaId, Long actividadId, LocalDate fecha, LocalTime horaInicio,
                                 int cantidad, List<String> nombres, List<String> apellidos,
                                 List<String> cis, List<String> fechasNacimiento) {

        if (turistaId == null || !turistaRepository.existsById(turistaId)) {
            return "Tenés que iniciar sesión como turista para reservar.";
        }

        Horario horario = horarioRepository.findById(new HorarioId(actividadId, fecha, horaInicio)).orElse(null);
        if (horario == null || !catalogoService.estaPublicada(horario.getActividad())) {
            return "El horario elegido no existe o la actividad no está disponible.";
        }

        if (!catalogoService.sePuedeReservar(horario, LocalDateTime.now())) {
            return "Ya pasó el plazo para reservar este horario.";
        }

        if (cantidad < 1) {
            return "Tenés que reservar al menos un cupo.";
        }

        // Primer control de cupos, para avisar antes de pedir los datos.
        // El control definitivo es el UPDATE atómico de crearReserva.
        if (cantidad > horario.getCuposDisponibles()) {
            return "No hay suficientes cupos: quedan " + horario.getCuposDisponibles() + ".";
        }

        // Un juego de datos por cada persona del grupo
        if (nombres == null || apellidos == null || cis == null || fechasNacimiento == null
                || nombres.size() != cantidad || apellidos.size() != cantidad
                || cis.size() != cantidad || fechasNacimiento.size() != cantidad) {
            return "Completá los datos de las " + cantidad + " personas del grupo.";
        }

        Set<String> cisUsadas = new HashSet<>();
        for (int i = 0; i < cantidad; i++) {
            int persona = i + 1;

            if (vacio(nombres.get(i)) || vacio(apellidos.get(i)) || vacio(cis.get(i)) || vacio(fechasNacimiento.get(i))) {
                return "Faltan datos de la persona " + persona + ".";
            }

            String ci = limpiarCi(cis.get(i));
            if (!ci.matches("[0-9A-Za-z]{5,20}")) {
                return "El documento de la persona " + persona + " no es válido.";
            }
            if (!cisUsadas.add(ci)) {
                return "El documento " + ci + " está repetido en el grupo.";
            }

            LocalDate nacimiento = convertirFecha(fechasNacimiento.get(i));
            if (nacimiento == null || nacimiento.isAfter(LocalDate.now())) {
                return "La fecha de nacimiento de la persona " + persona + " no es válida.";
            }
        }

        return null;   // todo correcto
    }

    // ---------- Crear ----------

    // Crea la reserva en estado PENDIENTE_PAGO y descuenta los cupos.
    // Devuelve la reserva guardada, o null si los datos no son válidos
    // o si justo otro turista se llevó los últimos cupos.
    // @Transactional: si algo falla a mitad de camino, se deshace todo
    // (no queda un cupo descontado sin reserva, ni una reserva sin su cupo).
    @Transactional
    public Reserva crearReserva(Long turistaId, Long actividadId, LocalDate fecha, LocalTime horaInicio,
                                int cantidad, List<String> nombres, List<String> apellidos,
                                List<String> cis, List<String> fechasNacimiento, String observacion) {

        if (validarReserva(turistaId, actividadId, fecha, horaInicio, cantidad,
                nombres, apellidos, cis, fechasNacimiento) != null) {
            return null;
        }

        // 1. Descontar los cupos de forma atómica (RNE #3).
        //    Si devuelve 0, alguien reservó antes y ya no alcanzan.
        int filas = horarioRepository.descontarCupos(actividadId, fecha, horaInicio, cantidad);
        if (filas == 0) {
            return null;
        }

        // 2. Armar la reserva (se vuelve a leer el horario, ya con los cupos actualizados)
        Horario horario = horarioRepository.findById(new HorarioId(actividadId, fecha, horaInicio)).orElseThrow();
        Turista turista = turistaRepository.findById(turistaId).orElseThrow();

        Reserva reserva = new Reserva();
        reserva.setTurista(turista);
        reserva.setHorario(horario);
        reserva.setCantCupos(cantidad);
        reserva.setObservacion(vacio(observacion) ? null : observacion.trim());
        reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
        reserva.setFechaCreacion(LocalDateTime.now());
        // Monto = precio de la actividad x cantidad de cupos (RNE #11)
        reserva.setMontoTotal(horario.getActividad().getPrecio().multiply(BigDecimal.valueOf(cantidad)));

        // 3. Un Participante por cada persona del grupo
        for (int i = 0; i < cantidad; i++) {
            Participante participante = new Participante();
            participante.setCi(limpiarCi(cis.get(i)));
            participante.setNombre(nombres.get(i).trim());
            participante.setApellido(apellidos.get(i).trim());
            participante.setFechaNacimiento(convertirFecha(fechasNacimiento.get(i)));
            reserva.agregarParticipante(participante);
        }

        // 4. Guardar (los participantes se guardan solos por el cascade)
        return reservaRepository.save(reserva);
    }

    // ---------- Consultar ----------

    // "Mis reservas" (CU-T09)
    @Transactional(readOnly = true)
    public List<Reserva> reservasDelTurista(Long turistaId) {
        return reservaRepository.findByTuristaIdOrderByFechaCreacionDesc(turistaId);
    }

    // Hasta cuándo tiene tiempo de pagar una reserva pendiente
    public LocalDateTime venceElPago(Reserva reserva) {
        return reserva.getFechaCreacion().plusHours(HORAS_PARA_PAGAR);
    }

    // ---------- Vencimiento automático (CU-S01) ----------

    // Se ejecuta solo cada 5 minutos (ver ConfigTareasProgramadas).
    // Cancela las reservas que llevan más de 2 horas sin pagarse y libera sus cupos (RNE #2).
    // Devuelve cuántas canceló.
    @Scheduled(fixedRate = 5 * 60 * 1000)
    @Transactional
    public int cancelarReservasVencidas() {
        LocalDateTime limite = LocalDateTime.now().minusHours(HORAS_PARA_PAGAR);
        List<Reserva> vencidas = reservaRepository.findByEstadoAndFechaCreacionBefore(EstadoReserva.PENDIENTE_PAGO, limite);

        for (Reserva reserva : vencidas) {
            reserva.setEstado(EstadoReserva.CANCELADA);
            reservaRepository.save(reserva);

            Horario horario = reserva.getHorario();
            horarioRepository.devolverCupos(horario.getActividad().getId(), horario.getFecha(),
                    horario.getHoraInicio(), reserva.getCantCupos());
        }
        return vencidas.size();
    }

    // ---------- Métodos de ayuda ----------

    private boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    // Saca puntos, guiones y espacios: "1.234.567-8" -> "12345678"
    private String limpiarCi(String ci) {
        return ci.replaceAll("[.\\-\\s]", "");
    }

    // Convierte "2001-05-30" (formato del <input type="date">) a LocalDate, o null si no es válida
    private LocalDate convertirFecha(String texto) {
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
