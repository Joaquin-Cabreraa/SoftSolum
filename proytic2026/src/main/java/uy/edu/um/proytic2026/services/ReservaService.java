package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.repository.HorarioRepository;
import uy.edu.um.proytic2026.repository.ReservaRepository;
import uy.edu.um.proytic2026.repository.TuristaRepository;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Reservas de los turistas.
// CU-T06 Reservar actividad, CU-T08 Cancelar reserva, CU-T09 Mis reservas,
// CU-S01 Liberar cupo por vencimiento, y la lista de espera (diferenciador).
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

    @Autowired
    private PagoService pagoService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // ---------- Validación ----------

    // Revisa todos los datos de la reserva. Devuelve el mensaje de error,
    // o null si está todo bien. (Mismo estilo que TuristaService.validarDatos)
    // Las listas traen un dato por participante, en el mismo orden.
    public String validarReserva(Long turistaId, Long actividadId, LocalDate fecha, LocalTime horaInicio,
                                 int cantidad, List<String> nombres, List<String> apellidos,
                                 List<String> cis, List<String> fechasNacimiento) {
        return validar(turistaId, actividadId, fecha, horaInicio, cantidad,
                nombres, apellidos, cis, fechasNacimiento, false);
    }

    // Igual que validarReserva, pero para anotarse en la lista de espera:
    // ahí justamente NO tiene que haber cupos suficientes.
    public String validarListaDeEspera(Long turistaId, Long actividadId, LocalDate fecha, LocalTime horaInicio,
                                       int cantidad, List<String> nombres, List<String> apellidos,
                                       List<String> cis, List<String> fechasNacimiento) {
        return validar(turistaId, actividadId, fecha, horaInicio, cantidad,
                nombres, apellidos, cis, fechasNacimiento, true);
    }

    private String validar(Long turistaId, Long actividadId, LocalDate fecha, LocalTime horaInicio,
                           int cantidad, List<String> nombres, List<String> apellidos,
                           List<String> cis, List<String> fechasNacimiento, boolean listaDeEspera) {

        if (turistaId == null || !turistaRepository.existsById(turistaId)) {
            return "Tenés que iniciar sesión como turista para reservar.";
        }

        Horario horario = horarioRepository.findById(new HorarioId(actividadId, fecha, horaInicio)).orElse(null);
        if (horario == null || !catalogoService.estaPublicada(horario.getActividad())) {
            return "El horario elegido no existe o la actividad no está disponible.";
        }

        if (!horario.disponible()) {
            return "El prestador canceló esta salida.";
        }
        if (!catalogoService.sePuedeReservar(horario, LocalDateTime.now())) {
            return "Ya pasó el plazo para reservar este horario.";
        }

        if (cantidad < 1) {
            return "Tenés que reservar al menos un cupo.";
        }

        // Una cuenta no reserva dos veces el mismo horario (si quiere sumar gente, cancela y reserva de nuevo)
        if (reservaRepository.turistaTieneReservaEnHorario(turistaId, actividadId, fecha, horaInicio, EstadoReserva.CANCELADA)) {
            return "Ya tenés una reserva para este horario. La podés ver en Mis reservas.";
        }

        if (listaDeEspera) {
            // Si hay lugar, que reserve directamente
            if (cantidad <= horario.getCuposDisponibles()) {
                return "Todavía hay lugar en este horario: podés reservar directamente.";
            }
            if (cantidad > horario.getCuposTotales()) {
                return "El horario tiene " + horario.getCuposTotales() + " lugares en total: no entra un grupo de " + cantidad + ".";
            }
        } else if (cantidad > horario.getCuposDisponibles()) {
            // Primer control de cupos, para avisar antes de pedir los datos.
            // El control definitivo es el UPDATE atómico de crearReserva.
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
            String errorDocumento = Documentos.validar(cis.get(i));
            if (errorDocumento != null) {
                return "Persona " + persona + ": " + errorDocumento;
            }
            if (!cisUsadas.add(ci)) {
                return "El documento " + ci + " está repetido en el grupo.";
            }

            LocalDate nacimiento = convertirFecha(fechasNacimiento.get(i));
            if (nacimiento == null || nacimiento.isAfter(LocalDate.now())) {
                return "La fecha de nacimiento de la persona " + persona + " no es válida.";
            }
        }

        // Cada persona ocupa un solo lugar: nadie del grupo puede tener ya un lugar en este horario
        List<String> yaAnotados = reservaRepository.documentosEnHorario(actividadId, fecha, horaInicio, EstadoReserva.CANCELADA);
        for (String ci : cisUsadas) {
            if (yaAnotados.contains(ci)) {
                return "La persona con documento " + ci + " ya tiene un lugar en este horario.";
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

        // 1. Descontar los cupos de forma atómica
        int filas = horarioRepository.descontarCupos(
                actividadId, fecha, horaInicio, cantidad
        );

        if (filas == 0) {
            return null;
        }

        // 2. Armar la reserva
        Horario horario = horarioRepository.findById(
                new HorarioId(actividadId, fecha, horaInicio)
        ).orElseThrow();

        LocalDateTime ahora = LocalDateTime.now();

        Reserva reserva = armarReserva(
                turistaId, horario, cantidad,
                nombres, apellidos, cis, fechasNacimiento, observacion
        );

        reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
        reserva.setPlazoPagoDesde(ahora);
        reserva.setMontoTotal(calcularMonto(horario, cantidad, ahora));

        // 3. Guardar la reserva
        Reserva reservaGuardada = reservaRepository.save(reserva);

        // 4. Publicar el evento de correo
        // El listener lo enviará después de confirmar la transacción
        eventPublisher.publishEvent(new ReservaCreadaEvent(
                reservaGuardada.getTurista().getUserEmail(),
                reservaGuardada.getHorario().getActividad().getTitulo(),
                reservaGuardada.getHorario().getFecha(),
                reservaGuardada.getHorario().getHoraInicio(),
                reservaGuardada.getCantCupos(),
                reservaGuardada.getMontoTotal()
        ));

        return reservaGuardada;
    }


    // ---------- Lista de espera ----------

    // El horario está lleno: el turista deja sus datos y queda EN_ESPERA, sin cupo retenido
    // y sin pagar nada. Cuando se libere lugar, se le asigna solo (ver promoverListaDeEspera).
    @Transactional
    public Reserva anotarEnListaDeEspera(Long turistaId, Long actividadId, LocalDate fecha, LocalTime horaInicio,
                                         int cantidad, List<String> nombres, List<String> apellidos,
                                         List<String> cis, List<String> fechasNacimiento, String observacion) {

        if (validarListaDeEspera(turistaId, actividadId, fecha, horaInicio, cantidad,
                nombres, apellidos, cis, fechasNacimiento) != null) {
            return null;
        }

        Horario horario = horarioRepository.findById(new HorarioId(actividadId, fecha, horaInicio)).orElseThrow();
        Reserva reserva = armarReserva(turistaId, horario, cantidad, nombres, apellidos, cis, fechasNacimiento, observacion);
        reserva.setEstado(EstadoReserva.EN_ESPERA);
        // Monto estimado: se vuelve a calcular cuando se le asigne el lugar

        reserva.setMontoTotal(calcularMonto(horario, cantidad, LocalDateTime.now()));

        Reserva reservaGuardada = reservaRepository.save(reserva);

        eventPublisher.publishEvent(new ListaEsperaRegistradaEvent(
                reservaGuardada.getTurista().getUserEmail(),
                reservaGuardada.getHorario().getActividad().getTitulo(),
                reservaGuardada.getHorario().getFecha(),
                reservaGuardada.getHorario().getHoraInicio(),
                reservaGuardada.getCantCupos()
        ));

        return reservaGuardada;

    }

    // Se liberaron lugares en un horario: se le ofrecen a la lista de espera, por orden de llegada.
    // Si el primero de la lista no entra (es un grupo más grande que lo que se liberó),
    // se prueba con el siguiente. Cada uno que entra pasa a PENDIENTE_PAGO y tiene 2 horas para pagar.
    // Devuelve a cuántos se les asignó lugar.
    @Transactional
    public int promoverListaDeEspera(Long actividadId, LocalDate fecha, LocalTime horaInicio) {
        List<Reserva> enEspera = reservaRepository.delHorarioEnEstado(EstadoReserva.EN_ESPERA, actividadId, fecha, horaInicio);
        LocalDateTime ahora = LocalDateTime.now();
        int promovidas = 0;

        for (Reserva reserva : enEspera) {
            if (!catalogoService.sePuedeReservar(reserva.getHorario(), ahora)) {
                break;   // ya cerró la reserva de este horario
            }
            // Mismo descuento atómico que una reserva normal: si no entra, devuelve 0

            if (horarioRepository.descontarCupos(
                    actividadId, fecha, horaInicio, reserva.getCantCupos()) == 1) {

                reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
                reserva.setPlazoPagoDesde(ahora);
                reserva.setMontoTotal(calcularMonto(
                        reserva.getHorario(), reserva.getCantCupos(), ahora
                ));

                reservaRepository.save(reserva);

                // Avisar que consiguió un cupo desde la lista de espera
                eventPublisher.publishEvent(new CupoAsignadoEvent(
                        reserva.getTurista().getUserEmail(),
                        reserva.getHorario().getActividad().getTitulo(),
                        reserva.getHorario().getFecha(),
                        reserva.getHorario().getHoraInicio(),
                        reserva.getCantCupos(),
                        reserva.getMontoTotal()
                ));

                promovidas++;
            }

        }
        return promovidas;
    }

    // Lugar en la fila (1 = el próximo en recibir lugar). 0 si no está en espera.
    @Transactional(readOnly = true)
    public int posicionEnListaDeEspera(Reserva reserva) {
        if (reserva.getEstado() != EstadoReserva.EN_ESPERA) return 0;
        Horario h = reserva.getHorario();
        List<Reserva> fila = reservaRepository.delHorarioEnEstado(EstadoReserva.EN_ESPERA,
                h.getActividad().getId(), h.getFecha(), h.getHoraInicio());
        for (int i = 0; i < fila.size(); i++) {
            if (fila.get(i).getId().equals(reserva.getId())) return i + 1;
        }
        return 0;
    }

    // ---------- Cancelar (CU-T08) ----------

    // El turista cancela una reserva suya. Devuelve el mensaje de error, o null si se canceló.
    // - EN_ESPERA: sale de la fila.
    // - PENDIENTE_PAGO: libera los cupos (y se los ofrece a la lista de espera).
    // - CONFIRMADA: solo si el horario admite cancelación y está a tiempo; se reembolsa el
    //   porcentaje que definió el prestador y se liberan los cupos.

    @Transactional
    public String cancelarReserva(Long turistaId, Long reservaId) {

        Reserva reserva = reservaRepository.findById(reservaId).orElse(null);

        if (reserva == null || !reserva.getTurista().getId().equals(turistaId)) {
            return "La reserva no existe.";
        }

        String error = puedeCancelar(reserva, LocalDateTime.now());
        if (error != null) {
            return error;
        }

        boolean estabaPaga = reserva.getEstado() == EstadoReserva.CONFIRMADA;

        BigDecimal montoReembolsado = estabaPaga
                ? montoAReembolsar(reserva)
                : BigDecimal.ZERO;

        if (reserva.getEstado() == EstadoReserva.EN_ESPERA) {
            reserva.setEstado(EstadoReserva.CANCELADA);
            reservaRepository.save(reserva);

        } else {

            if (estabaPaga) {
                error = pagoService.reembolsar(reserva, montoReembolsado);

                if (error != null) {
                    return error;
                }
            }

            reserva.setEstado(EstadoReserva.CANCELADA);
            reservaRepository.save(reserva);

            liberarCupos(reserva);
        }

        // Enviar correo únicamente si la cancelación fue exitosa.
        // El listener espera a que se confirme la transacción.
        eventPublisher.publishEvent(new ReservaCanceladaEvent(
                reserva.getTurista().getUserEmail(),
                reserva.getHorario().getActividad().getTitulo(),
                reserva.getHorario().getFecha(),
                reserva.getHorario().getHoraInicio(),
                false,
                estabaPaga,
                montoReembolsado,
                null
        ));
        return null;
    }


    // ---------- El prestador cancela una salida completa ----------

    // Cancela todas las reservas activas del horario. A las pagas se les devuelve el 100 %.
    // Devuelve cuántas reservas pagas NO se pudieron reembolsar (0 = todo bien); esas quedan
    // confirmadas para reintentar. Los controles (dueño, plazo) los hace ActividadService.

    @Transactional
    public int cancelarPorPrestador(Horario horario, String motivo) {

        List<Reserva> reservas = reservaRepository.delHorarioNoCanceladas(
                horario.getActividad().getId(),
                horario.getFecha(),
                horario.getHoraInicio(),
                EstadoReserva.CANCELADA
        );

        int sinReembolsar = 0;

        for (Reserva reserva : reservas) {

            boolean estabaPaga = reserva.getEstado() == EstadoReserva.CONFIRMADA;

            if (estabaPaga) {
                String error = pagoService.reembolsar(
                        reserva,
                        reserva.getMontoTotal()
                );

                if (error != null) {
                    sinReembolsar++;
                    continue;
                }
            }

            reserva.setEstado(EstadoReserva.CANCELADA);
            reserva.setMotivoCancelacionPrestador(motivo);
            reservaRepository.save(reserva);

            // Avisar al turista después de confirmar la transacción.
            eventPublisher.publishEvent(new ReservaCanceladaEvent(
                    reserva.getTurista().getUserEmail(),
                    reserva.getHorario().getActividad().getTitulo(),
                    reserva.getHorario().getFecha(),
                    reserva.getHorario().getHoraInicio(),
                    true,
                    estabaPaga,
                    estabaPaga ? reserva.getMontoTotal() : BigDecimal.ZERO,
                    motivo
            ));
        }

        return sinReembolsar;
    }


    // ¿Se puede cancelar esta reserva AHORA? null = sí; si no, el motivo (se muestra en "Mis reservas").
    // - Nada se cancela una vez que la actividad empezó.
    // - En espera o pendiente de pago: se puede hasta el inicio (no se pagó nada).
    // - Confirmada (paga): según la política que eligió el prestador para ese horario.
    public String puedeCancelar(Reserva reserva, LocalDateTime ahora) {
        Horario h = reserva.getHorario();
        if (reserva.getEstado() == EstadoReserva.CANCELADA) {
            return "La reserva ya estaba cancelada.";
        }
        if (!ahora.isBefore(h.inicio())) {
            return "La actividad ya empezó: no se puede cancelar.";
        }
        if (reserva.getEstado() == EstadoReserva.CONFIRMADA) {
            return puedeCancelarConfirmada(reserva, ahora);
        }
        return null;
    }

    // Reglas para una reserva ya pagada. null = se puede; si no, el motivo.
    public String puedeCancelarConfirmada(Reserva reserva, LocalDateTime ahora) {
        Horario h = reserva.getHorario();
        if (!Boolean.TRUE.equals(h.getCancelable())) {
            return "Este horario no admite cancelación.";
        }
        if (!ahora.isBefore(h.limiteCancelacion())) {
            return "Ya pasó el plazo para cancelar esta reserva.";
        }
        return null;
    }

    // Lo que se devuelve al cancelar una reserva pagada: el % que eligió el prestador
    public BigDecimal montoAReembolsar(Reserva reserva) {
        int porcentaje = reserva.getHorario().reembolso();
        if (porcentaje <= 0) return BigDecimal.ZERO;
        return reserva.getMontoTotal().multiply(BigDecimal.valueOf(porcentaje))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    // ---------- Consultar ----------

    // "Mis reservas" (CU-T09)
    @Transactional(readOnly = true)
    public List<Reserva> reservasDelTurista(Long turistaId) {
        return reservaRepository.findByTuristaIdOrderByFechaCreacionDesc(turistaId);
    }

    // Código del QR. Las reservas hechas antes de que existiera el QR no tienen: se les crea acá.
    @Transactional
    public String codigoDe(Reserva reserva) {
        if (reserva.getCodigo() == null) {
            reserva.setCodigo(java.util.UUID.randomUUID().toString());
            reservaRepository.save(reserva);
        }
        return reserva.getCodigo();
    }

    // Comprobante público del QR (null si el código no existe)
    @Transactional(readOnly = true)
    public Reserva reservaPorCodigo(String codigo) {
        Reserva reserva = codigo == null ? null : reservaRepository.findByCodigo(codigo).orElse(null);
        if (reserva != null) {
            reserva.getParticipantes().size();   // cargar los participantes para la página
        }
        return reserva;
    }

    // Hasta cuándo tiene tiempo de pagar una reserva pendiente
    public LocalDateTime venceElPago(Reserva reserva) {
        return reserva.inicioPlazoPago().plusHours(HORAS_PARA_PAGAR);
    }

    // ---------- Vencimiento automático (CU-S01) ----------

    // Se ejecuta solo cada 5 minutos (ver ConfigTareasProgramadas).
    // Cancela las reservas que llevan más de 2 horas sin pagarse y libera sus cupos (RNE #2).
    // Los lugares liberados se le ofrecen a la lista de espera.
    // También saca de la lista de espera a quienes esperaban un horario que ya cerró.
    // Devuelve cuántas reservas pendientes canceló.
    @Scheduled(fixedRate = 5 * 60 * 1000)
    @Transactional
    public int cancelarReservasVencidas() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limite = ahora.minusHours(HORAS_PARA_PAGAR);
        List<Reserva> vencidas = reservaRepository.pendientesConPlazoVencido(EstadoReserva.PENDIENTE_PAGO, limite);

        for (Reserva reserva : vencidas) {

            reserva.setEstado(EstadoReserva.CANCELADA);
            reservaRepository.save(reserva);

            liberarCupos(reserva);

            // Avisar al turista que su reserva venció
            eventPublisher.publishEvent(new ReservaVencidaEvent(
                    reserva.getTurista().getUserEmail(),
                    reserva.getHorario().getActividad().getTitulo(),
                    reserva.getHorario().getFecha(),
                    reserva.getHorario().getHoraInicio()
            ));
        }


        for (Reserva espera : reservaRepository.findByEstado(EstadoReserva.EN_ESPERA)) {
            if (!catalogoService.sePuedeReservar(espera.getHorario(), ahora)) {
                espera.setEstado(EstadoReserva.CANCELADA);
                reservaRepository.save(espera);
            }
        }
        return vencidas.size();
    }

    // ---------- Métodos de ayuda ----------

    // Devuelve los cupos de una reserva cancelada y se los ofrece a la lista de espera
    private void liberarCupos(Reserva reserva) {
        Horario horario = reserva.getHorario();
        Long actividadId = horario.getActividad().getId();
        horarioRepository.devolverCupos(actividadId, horario.getFecha(), horario.getHoraInicio(), reserva.getCantCupos());
        promoverListaDeEspera(actividadId, horario.getFecha(), horario.getHoraInicio());
    }

    private BigDecimal calcularMonto(Horario horario, int cantidad, LocalDateTime ahora) {
        return horario.precioPorPersona(ahora).multiply(BigDecimal.valueOf(cantidad));
    }

    // Arma la reserva con sus participantes (sin estado ni monto)
    private Reserva armarReserva(Long turistaId, Horario horario, int cantidad, List<String> nombres,
                                 List<String> apellidos, List<String> cis, List<String> fechasNacimiento,
                                 String observacion) {
        Turista turista = turistaRepository.findById(turistaId).orElseThrow();

        Reserva reserva = new Reserva();
        reserva.setTurista(turista);
        reserva.setHorario(horario);
        reserva.setCantCupos(cantidad);
        reserva.setObservacion(vacio(observacion) ? null : observacion.trim());
        reserva.setFechaCreacion(LocalDateTime.now());
        reserva.setCodigo(java.util.UUID.randomUUID().toString());   // para el código QR

        // Un Participante por cada persona del grupo
        for (int i = 0; i < cantidad; i++) {
            Participante participante = new Participante();
            participante.setCi(limpiarCi(cis.get(i)));
            participante.setNombre(nombres.get(i).trim());
            participante.setApellido(apellidos.get(i).trim());
            participante.setFechaNacimiento(convertirFecha(fechasNacimiento.get(i)));
            reserva.agregarParticipante(participante);
        }
        return reserva;
    }

    private boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    // Saca puntos, guiones y espacios: "1.234.567-8" -> "12345678"
    private String limpiarCi(String ci) {
        return Documentos.limpiar(ci);
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
