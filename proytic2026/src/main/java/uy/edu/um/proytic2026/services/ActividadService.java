package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// Gestión de actividades y horarios desde el espacio del prestador.
// CU-P06 Alta de actividad, CU-P07 Editar, CU-P08 Baja, CU-P09 Cargar horario,
// CU-P11 Consultar reservas de sus actividades.
@Service
public class ActividadService {

    // Los 19 departamentos (se eligen de una lista para que todos se escriban igual
    // y el filtro por departamento del portal funcione)
    public static final List<String> DEPARTAMENTOS = List.of(
            "Artigas", "Canelones", "Cerro Largo", "Colonia", "Durazno", "Flores", "Florida",
            "Lavalleja", "Maldonado", "Montevideo", "Paysandú", "Río Negro", "Rivera", "Rocha",
            "Salto", "San José", "Soriano", "Tacuarembó", "Treinta y Tres");

    public static final String ACTIVA = "ACTIVA";
    public static final String BAJA = "BAJA";
    // Eliminada por el prestador pero con reservas viejas: se oculta de todos lados
    // (no se borra para no perder el historial de esas reservas y sus pagos)
    public static final String ELIMINADA = "ELIMINADA";

    @Autowired private ActividadRepository actividadRepository;
    @Autowired private HorarioRepository horarioRepository;
    @Autowired private PrestadorRepository prestadorRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private TipoActividadRepository tipoActividadRepository;
    @Autowired private TipoEquipamientoRepository tipoEquipamientoRepository;
    @Autowired private ReservaService reservaService;
    @Autowired private uy.edu.um.proytic2026.repository.ImagenActividadRepository imagenActividadRepository;

    // ---------- Emprendimiento del prestador ----------

    // El emprendimiento del prestador logueado (o null si no tiene)
    @Transactional(readOnly = true)
    public Emprendimiento emprendimientoDe(Long prestadorId) {
        Prestador prestador = prestadorRepository.findById(prestadorId).orElse(null);
        return prestador == null ? null : prestador.getEmprendimiento();
    }

    // Solo un emprendimiento ACEPTADO puede publicar actividades (RNE #8)
    public boolean puedePublicar(Emprendimiento emprendimiento) {
        return emprendimiento != null && emprendimiento.getEstado() == EstadoEmprendimiento.ACEPTADO;
    }

    // Devuelve la actividad solo si es de este prestador (para que nadie edite actividades ajenas)
    @Transactional(readOnly = true)
    public Actividad actividadDelPrestador(Long prestadorId, Long actividadId) {
        Emprendimiento emprendimiento = emprendimientoDe(prestadorId);
        Actividad actividad = actividadRepository.findById(actividadId).orElse(null);
        if (emprendimiento == null || actividad == null
                || !actividad.getEmprendimiento().getId().equals(emprendimiento.getId())) {
            return null;
        }
        return actividad;
    }

    @Transactional(readOnly = true)
    public List<Actividad> actividadesDelPrestador(Long prestadorId) {
        Emprendimiento emprendimiento = emprendimientoDe(prestadorId);
        if (emprendimiento == null) return new ArrayList<>();
        List<Actividad> lista = new ArrayList<>(actividadRepository.findByEmprendimientoIdOrderByIdDesc(emprendimiento.getId()));
        lista.removeIf(a -> ELIMINADA.equals(a.getEstado()));
        return lista;
    }

    // Reservas que impiden editar: no canceladas y de salidas que todavía no pasaron.
    // (Las canceladas o de salidas ya hechas no afectan a nadie si se cambia la actividad.)
    public boolean tieneReservas(Actividad actividad) {
        return reservaRepository.actividadTieneReservasVigentes(actividad.getId(), EstadoReserva.CANCELADA, LocalDate.now());
    }

    // ---------- Validación de los datos de una actividad ----------

    // Devuelve el mensaje de error, o null si los datos están bien
    public String validarDatos(String titulo, String descripcion, String departamento,
                               BigDecimal precio, Integer minPreviosReservar) {
        if (vacio(titulo) || vacio(descripcion) || vacio(departamento)) {
            return "Completá el título, la descripción y el departamento.";
        }
        if (titulo.trim().length() > 120) {
            return "El título puede tener como máximo 120 caracteres.";
        }
        if (!DEPARTAMENTOS.contains(departamento)) {
            return "Elegí un departamento de la lista.";
        }
        if (precio == null || precio.compareTo(BigDecimal.ZERO) < 0) {
            return "Ingresá un precio válido (0 o más).";
        }
        if (minPreviosReservar == null || minPreviosReservar < 0) {
            return "Los minutos previos para reservar tienen que ser 0 o más.";
        }
        return null;
    }

    // ---------- Alta (CU-P06) ----------

    // Crea la actividad. Devuelve la actividad, o null si no se pudo
    // (emprendimiento no aceptado o datos inválidos).
    @Transactional
    public Actividad crearActividad(Long prestadorId, String titulo, String descripcion, String departamento,
                                    String localidad, BigDecimal precio,
                                    String requisitosEdad, Integer minPreviosReservar,
                                    List<Long> tiposActividadIds, List<Long> tiposEquipamientoIds) {

        Emprendimiento emprendimiento = emprendimientoDe(prestadorId);
        if (!puedePublicar(emprendimiento)) {
            return null;
        }
        if (validarDatos(titulo, descripcion, departamento, precio, minPreviosReservar) != null) {
            return null;
        }

        Actividad actividad = new Actividad();
        actividad.setEmprendimiento(emprendimiento);
        actividad.setEstado(ACTIVA);
        copiarDatos(actividad, titulo, descripcion, departamento, localidad, precio,
                requisitosEdad, minPreviosReservar, tiposActividadIds, tiposEquipamientoIds);

        return actividadRepository.save(actividad);
    }

    // ---------- Edición (CU-P07) ----------

    // Devuelve el mensaje de error, o null si se guardó bien
    @Transactional
    public String editarActividad(Long prestadorId, Long actividadId, String titulo, String descripcion,
                                  String departamento, String localidad,
                                  BigDecimal precio, String requisitosEdad, Integer minPreviosReservar,
                                  List<Long> tiposActividadIds, List<Long> tiposEquipamientoIds) {

        Actividad actividad = actividadDelPrestador(prestadorId, actividadId);
        if (actividad == null) {
            return "La actividad no existe.";
        }
        if (BAJA.equals(actividad.getEstado())) {
            return "La actividad está dada de baja.";
        }
        // Si ya tiene reservas no se puede modificar: los turistas reservaron
        // con esas condiciones y ese precio (RNE #15)
        if (tieneReservas(actividad)) {
            return "La actividad tiene reservas vigentes: no se puede modificar hasta que pasen esas salidas. Sí podés agregarle horarios.";
        }
        String error = validarDatos(titulo, descripcion, departamento, precio, minPreviosReservar);
        if (error != null) {
            return error;
        }

        copiarDatos(actividad, titulo, descripcion, departamento, localidad, precio,
                requisitosEdad, minPreviosReservar, tiposActividadIds, tiposEquipamientoIds);
        actividadRepository.save(actividad);
        return null;
    }

    // ---------- Descuento de última hora (diferenciador) ----------

    // Devuelve el error, o null si está bien. Los dos vacíos = sin descuento.
    public String validarUltimaHora(Integer descuento, Integer horas) {
        if (descuento == null && horas == null) return null;
        if (descuento == null || horas == null) {
            return "Para el descuento de última hora completá el porcentaje y las horas (o dejá los dos vacíos).";
        }
        if (descuento < 1 || descuento > 90) return "El descuento tiene que ser entre 1 % y 90 %.";
        if (horas < 1 || horas > 72) return "Las horas antes del inicio tienen que ser entre 1 y 72.";
        return null;
    }

    // Se puede cambiar aunque la actividad tenga reservas: solo afecta a las reservas nuevas
    @Transactional
    public String configurarUltimaHora(Long prestadorId, Long actividadId, Integer descuento, Integer horas) {
        Actividad actividad = actividadDelPrestador(prestadorId, actividadId);
        if (actividad == null) return "La actividad no existe.";
        String error = validarUltimaHora(descuento, horas);
        if (error != null) return error;
        actividad.setDescuentoUltimaHora(descuento);
        actividad.setHorasUltimaHora(horas);
        actividadRepository.save(actividad);
        return null;
    }

    // ---------- Baja (CU-P08) ----------

    // Baja lógica: no se borra (se conservan las reservas hechas, RNE #9),
    // solo deja de mostrarse en el portal.
    @Transactional
    public boolean darDeBaja(Long prestadorId, Long actividadId) {
        Actividad actividad = actividadDelPrestador(prestadorId, actividadId);
        if (actividad == null || BAJA.equals(actividad.getEstado()) || motivoParaNoPausar(actividad) != null) {
            return false;
        }
        actividad.setEstado(BAJA);
        actividadRepository.save(actividad);
        return true;
    }

    // Solo se puede pausar si no quedan salidas próximas abiertas: si no, los turistas que
    // reservaron se quedarían con una actividad que "no existe" en el portal.
    // Devuelve el motivo para no pausar, o null si se puede.
    public String motivoParaNoPausar(Actividad actividad) {
        long activos = horariosActivos(actividad);
        if (activos == 0) return null;
        return "Tiene " + activos + (activos == 1 ? " horario próximo" : " horarios próximos")
                + ": eliminalos o cancelá esas salidas antes de pausar la actividad.";
    }

    // Horarios futuros que siguen en pie (ni cancelados ni eliminados)
    public long horariosActivos(Actividad actividad) {
        LocalDateTime ahora = LocalDateTime.now();
        return actividad.getHorarios().stream()
                .filter(h -> h.disponible() && h.inicio().isAfter(ahora))
                .count();
    }

    // El prestador elimina una actividad que tiene pausada.
    // - Si nunca tuvo reservas, se borra de verdad (con sus horarios y fotos).
    // - Si tuvo reservas (canceladas o ya hechas), queda ELIMINADA: no se ve en ningún lado,
    //   pero se conserva para el historial de esas reservas y sus pagos.
    // Devuelve el error, o null si se eliminó.
    @Transactional
    public String eliminarActividad(Long prestadorId, Long actividadId) {
        Actividad actividad = actividadDelPrestador(prestadorId, actividadId);
        if (actividad == null || ELIMINADA.equals(actividad.getEstado())) return "La actividad no existe.";
        if (!BAJA.equals(actividad.getEstado())) return "Primero pausá la actividad; después la podés eliminar.";
        if (actividad.quitadaPorAdministrador()) {
            return "Esta actividad la quitó el administrador: contactalo antes de eliminarla.";
        }
        if (reservaRepository.actividadTieneReservas(actividadId)) {
            actividad.setEstado(ELIMINADA);
            actividadRepository.save(actividad);
            return null;
        }
        imagenActividadRepository.deleteAll(imagenActividadRepository.findAllById(
                imagenActividadRepository.idsDeActividad(actividadId)));
        actividadRepository.delete(actividad);   // los horarios se borran en cascada
        return null;
    }

    // El prestador vuelve a publicar una actividad que había pausado.
    // No puede si la quitó el administrador. Devuelve el error, o null si se publicó.
    @Transactional
    public String reactivar(Long prestadorId, Long actividadId) {
        Actividad actividad = actividadDelPrestador(prestadorId, actividadId);
        if (actividad == null) return "La actividad no existe.";
        if (!BAJA.equals(actividad.getEstado())) return "La actividad ya está publicada.";
        if (actividad.quitadaPorAdministrador()) {
            return "Esta actividad la quitó el administrador del portal: escribile para revisarla.";
        }
        actividad.setEstado(ACTIVA);
        actividadRepository.save(actividad);
        return null;
    }

    // ---------- Equipamiento incluido (lo provee el prestador) ----------

    @Transactional
    public void actualizarEquipamientoIncluido(Long prestadorId, Long actividadId, List<Long> ids) {
        Actividad actividad = actividadDelPrestador(prestadorId, actividadId);
        if (actividad == null) return;
        actividad.getEquipamientoIncluido().clear();
        if (ids != null) {
            for (Long id : ids) {
                tipoEquipamientoRepository.findById(id).ifPresent(t -> {
                    if (!actividad.getEquipamientoIncluido().contains(t)) actividad.getEquipamientoIncluido().add(t);
                });
            }
        }
        actividadRepository.save(actividad);
    }

    // ---------- Horarios (CU-P09) ----------

    @Transactional(readOnly = true)
    public List<Horario> horariosDe(Actividad actividad) {
        List<Horario> lista = new java.util.ArrayList<>(horarioRepository.findByActividadIdAndFechaGreaterThanEqualOrderByFechaAscHoraInicioAsc(
                actividad.getId(), LocalDate.now().minusDays(30)));
        lista.removeIf(Horario::estaEliminado);   // los eliminados no se muestran
        return lista;
    }

    // Devuelve el mensaje de error, o null si los datos del horario están bien
    public String validarHorario(Actividad actividad, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
                                 Integer cupos, boolean cancelable, Integer minMinutosCancelacion,
                                 Integer porcentajeReembolso) {
        if (actividad == null) {
            return "La actividad no existe.";
        }
        if (BAJA.equals(actividad.getEstado())) {
            return "La actividad está dada de baja.";
        }
        if (fecha == null || horaInicio == null || horaFin == null) {
            return "Completá la fecha y las horas.";
        }
        if (!fecha.atTime(horaInicio).isAfter(LocalDateTime.now())) {
            return "El horario tiene que ser en el futuro.";
        }
        if (!horaFin.isAfter(horaInicio)) {
            return "La hora de fin tiene que ser después de la de inicio.";
        }
        if (cupos == null || cupos < 1) {
            return "Ingresá la cantidad de cupos (1 o más).";
        }
        Horario existente = horarioRepository.findById(new HorarioId(actividad.getId(), fecha, horaInicio)).orElse(null);
        if (existente != null && !existente.estaEliminado()) {
            return "Ya existe un horario para esa fecha y hora de inicio.";
        }
        // Política de cancelación (acta P8-P9)
        if (cancelable) {
            if (minMinutosCancelacion == null || minMinutosCancelacion < 0) {
                return "Indicá hasta cuántas horas antes del inicio se puede cancelar.";
            }
            if (porcentajeReembolso == null || porcentajeReembolso < 0 || porcentajeReembolso > 100) {
                return "El porcentaje de reembolso tiene que estar entre 0 y 100.";
            }
            // Si el plazo ya pasó hoy, nadie podría cancelar: seguramente es un error al cargarlo
            if (!fecha.atTime(horaInicio).minusMinutes(minMinutosCancelacion).isAfter(LocalDateTime.now())) {
                return "Con esa anticipación ya no se podría cancelar nunca (el horario empieza antes). "
                        + "Poné menos horas o elegí \"No admite cancelación\".";
            }
        }
        return null;
    }

    // Crea el horario. Al principio los cupos disponibles son iguales a los totales.
    @Transactional
    public Horario agregarHorario(Long prestadorId, Long actividadId, LocalDate fecha, LocalTime horaInicio,
                                  LocalTime horaFin, Integer cupos, boolean cancelable,
                                  Integer minMinutosCancelacion, Integer porcentajeReembolso) {

        Actividad actividad = actividadDelPrestador(prestadorId, actividadId);
        if (validarHorario(actividad, fecha, horaInicio, horaFin, cupos, cancelable,
                minMinutosCancelacion, porcentajeReembolso) != null) {
            return null;
        }

        // Si había uno eliminado en esa misma fecha y hora, se reutiliza (la clave es actividad + fecha + hora)
        Horario horario = horarioRepository.findById(new HorarioId(actividadId, fecha, horaInicio)).orElse(new Horario());
        horario.setEliminado(null);
        horario.setCancelado(null);
        horario.setMotivoCancelacion(null);
        horario.setFechaCancelacion(null);
        horario.setActividad(actividad);
        horario.setFecha(fecha);
        horario.setHoraInicio(horaInicio);
        horario.setHoraFin(horaFin);
        horario.setCuposTotales(cupos);
        horario.setCuposDisponibles(cupos);
        horario.setCancelable(cancelable);
        horario.setMinMinutosCancelacion(cancelable ? minMinutosCancelacion : null);
        horario.setPorcentajeReembolso(cancelable ? porcentajeReembolso : null);

        return horarioRepository.save(horario);
    }

    // Borra un horario. Solo si todavía no tiene reservas (RNE #6).
    // Devuelve el mensaje de error, o null si se borró.
    // El prestador cancela una salida que ya tiene reservas (mal tiempo, imprevisto...).
    // Regla de la plataforma, conocida por turistas y prestadores: se puede hasta
    // 24 h antes del inicio y a todos los que pagaron se les devuelve el 100 %.
    // Devuelve el mensaje de error, o null si quedó todo cancelado.
    @Transactional
    public String cancelarHorario(Long prestadorId, Long actividadId, LocalDate fecha, LocalTime horaInicio, String motivo) {
        Actividad actividad = actividadDelPrestador(prestadorId, actividadId);
        if (actividad == null) return "La actividad no existe.";
        Horario horario = horarioRepository.findById(new HorarioId(actividadId, fecha, horaInicio)).orElse(null);
        if (horario == null) return "El horario no existe.";
        if (motivo == null || motivo.isBlank()) {
            return "Contales a los turistas por qué se cancela (ej. pronóstico de tormenta).";
        }
        // Si ya estaba cancelada se puede reintentar (por si algún reembolso falló)
        if (!horario.estaCancelado() && !LocalDateTime.now().isBefore(horario.limiteCancelacionPrestador())) {
            return "Faltan menos de " + Horario.HORAS_ANTES_CANCELAR_SALIDA + " h para la salida: ya no se puede "
                    + "cancelar desde la app. Comunicate con el administrador.";
        }
        String texto = motivo.trim().length() > 300 ? motivo.trim().substring(0, 300) : motivo.trim();
        horario.setCancelado(true);
        horario.setMotivoCancelacion(texto);
        if (horario.getFechaCancelacion() == null) horario.setFechaCancelacion(LocalDateTime.now());
        horario.setCuposDisponibles(0);   // nadie más puede reservar ni entrar desde la lista de espera
        horarioRepository.save(horario);

        int sinReembolsar = reservaService.cancelarPorPrestador(horario, texto);
        if (sinReembolsar > 0) {
            return "La salida quedó cancelada, pero no se pudo devolver la plata de " + sinReembolsar
                    + " reserva(s): la pasarela no respondió. Volvé a tocar \"Cancelar salida\" para reintentar.";
        }
        return null;
    }

    @Transactional
    public String eliminarHorario(Long prestadorId, Long actividadId, LocalDate fecha, LocalTime horaInicio) {
        Actividad actividad = actividadDelPrestador(prestadorId, actividadId);
        if (actividad == null) {
            return "La actividad no existe.";
        }
        HorarioId id = new HorarioId(actividadId, fecha, horaInicio);
        Horario horario = horarioRepository.findById(id).orElse(null);
        if (horario == null || horario.estaEliminado()) {
            return "El horario no existe.";
        }
        // Con reservas vigentes no se borra (RNE #6): para eso está "Cancelar salida"
        if (reservaRepository.horarioTieneReservasActivas(actividadId, fecha, horaInicio, EstadoReserva.CANCELADA)) {
            return "Ese horario tiene reservas vigentes: no se puede eliminar. Si no se va a hacer, usá \"Cancelar salida\".";
        }
        // Solo tiene reservas canceladas (vencidas o canceladas por el turista): se oculta en vez de
        // borrarlo, para no perder el historial de esas reservas y sus pagos
        if (reservaRepository.horarioTieneReservas(actividadId, fecha, horaInicio)) {
            horario.setEliminado(true);
            horario.setCuposDisponibles(0);
            horarioRepository.save(horario);
            return null;
        }
        horarioRepository.deleteById(id);
        return null;
    }

    // ---------- Reservas recibidas (CU-P11) ----------

    @Transactional(readOnly = true)
    public List<Reserva> reservasRecibidas(Long prestadorId) {
        Emprendimiento emprendimiento = emprendimientoDe(prestadorId);
        if (emprendimiento == null) return new ArrayList<>();
        return reservaRepository.reservasDelEmprendimiento(emprendimiento.getId());
    }

    // ---------- Métodos de ayuda ----------

    private void copiarDatos(Actividad actividad, String titulo, String descripcion, String departamento,
                             String localidad, BigDecimal precio,
                             String requisitosEdad, Integer minPreviosReservar,
                             List<Long> tiposActividadIds, List<Long> tiposEquipamientoIds) {
        actividad.setTitulo(titulo.trim());
        actividad.setDescripcion(descripcion.trim());
        actividad.setDepartamento(departamento);
        actividad.setLocalidad(vacio(localidad) ? null : localidad.trim());
        actividad.setPrecio(precio);
        actividad.setRequisitosEdad(vacio(requisitosEdad) ? null : requisitosEdad.trim());
        actividad.setMinPreviosReservar(minPreviosReservar);

        // Categorías y equipamiento elegidos (relaciones n:n del MER)
        actividad.getTipoActividad().clear();
        if (tiposActividadIds != null) {
            actividad.getTipoActividad().addAll(tipoActividadRepository.findAllById(tiposActividadIds));
        }
        actividad.getTiposEquipamiento().clear();
        if (tiposEquipamientoIds != null) {
            actividad.getTiposEquipamiento().addAll(tipoEquipamientoRepository.findAllById(tiposEquipamientoIds));
        }
    }

    private boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
