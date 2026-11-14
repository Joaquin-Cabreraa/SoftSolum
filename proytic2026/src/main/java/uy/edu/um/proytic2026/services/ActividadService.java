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

    @Autowired private ActividadRepository actividadRepository;
    @Autowired private HorarioRepository horarioRepository;
    @Autowired private PrestadorRepository prestadorRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private TipoActividadRepository tipoActividadRepository;
    @Autowired private TipoEquipamientoRepository tipoEquipamientoRepository;

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
        return actividadRepository.findByEmprendimientoIdOrderByIdDesc(emprendimiento.getId());
    }

    public boolean tieneReservas(Actividad actividad) {
        return reservaRepository.actividadTieneReservas(actividad.getId());
    }

    // ---------- Validación de los datos de una actividad ----------

    // Devuelve el mensaje de error, o null si los datos están bien
    public String validarDatos(String titulo, String descripcion, String departamento,
                               Integer duracionMinutos, BigDecimal precio, Integer minPreviosReservar) {
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
        if (duracionMinutos == null || duracionMinutos <= 0) {
            return "Ingresá la duración en minutos.";
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
                                    String localidad, Integer duracionMinutos, BigDecimal precio,
                                    String requisitosEdad, Integer minPreviosReservar,
                                    List<Long> tiposActividadIds, List<Long> tiposEquipamientoIds) {

        Emprendimiento emprendimiento = emprendimientoDe(prestadorId);
        if (!puedePublicar(emprendimiento)) {
            return null;
        }
        if (validarDatos(titulo, descripcion, departamento, duracionMinutos, precio, minPreviosReservar) != null) {
            return null;
        }

        Actividad actividad = new Actividad();
        actividad.setEmprendimiento(emprendimiento);
        actividad.setEstado(ACTIVA);
        copiarDatos(actividad, titulo, descripcion, departamento, localidad, duracionMinutos, precio,
                requisitosEdad, minPreviosReservar, tiposActividadIds, tiposEquipamientoIds);

        return actividadRepository.save(actividad);
    }

    // ---------- Edición (CU-P07) ----------

    // Devuelve el mensaje de error, o null si se guardó bien
    @Transactional
    public String editarActividad(Long prestadorId, Long actividadId, String titulo, String descripcion,
                                  String departamento, String localidad, Integer duracionMinutos,
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
            return "La actividad ya tiene reservas: no se puede modificar. Podés darla de baja o agregarle horarios.";
        }
        String error = validarDatos(titulo, descripcion, departamento, duracionMinutos, precio, minPreviosReservar);
        if (error != null) {
            return error;
        }

        copiarDatos(actividad, titulo, descripcion, departamento, localidad, duracionMinutos, precio,
                requisitosEdad, minPreviosReservar, tiposActividadIds, tiposEquipamientoIds);
        actividadRepository.save(actividad);
        return null;
    }

    // ---------- Baja (CU-P08) ----------

    // Baja lógica: no se borra (se conservan las reservas hechas, RNE #9),
    // solo deja de mostrarse en el portal.
    @Transactional
    public boolean darDeBaja(Long prestadorId, Long actividadId) {
        Actividad actividad = actividadDelPrestador(prestadorId, actividadId);
        if (actividad == null || BAJA.equals(actividad.getEstado())) {
            return false;
        }
        actividad.setEstado(BAJA);
        actividadRepository.save(actividad);
        return true;
    }

    // ---------- Horarios (CU-P09) ----------

    @Transactional(readOnly = true)
    public List<Horario> horariosDe(Actividad actividad) {
        return horarioRepository.findByActividadIdAndFechaGreaterThanEqualOrderByFechaAscHoraInicioAsc(
                actividad.getId(), LocalDate.now().minusDays(30));
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
        if (horarioRepository.existsById(new HorarioId(actividad.getId(), fecha, horaInicio))) {
            return "Ya existe un horario para esa fecha y hora de inicio.";
        }
        // Política de cancelación (acta P8-P9)
        if (cancelable) {
            if (minMinutosCancelacion == null || minMinutosCancelacion < 0) {
                return "Indicá con cuántos minutos de anticipación se puede cancelar.";
            }
            if (porcentajeReembolso == null || porcentajeReembolso < 0 || porcentajeReembolso > 100) {
                return "El porcentaje de reembolso tiene que estar entre 0 y 100.";
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

        Horario horario = new Horario();
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
    @Transactional
    public String eliminarHorario(Long prestadorId, Long actividadId, LocalDate fecha, LocalTime horaInicio) {
        Actividad actividad = actividadDelPrestador(prestadorId, actividadId);
        if (actividad == null) {
            return "La actividad no existe.";
        }
        HorarioId id = new HorarioId(actividadId, fecha, horaInicio);
        if (!horarioRepository.existsById(id)) {
            return "El horario no existe.";
        }
        if (reservaRepository.horarioTieneReservas(actividadId, fecha, horaInicio)) {
            return "Ese horario ya tiene reservas: no se puede eliminar.";
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
                             String localidad, Integer duracionMinutos, BigDecimal precio,
                             String requisitosEdad, Integer minPreviosReservar,
                             List<Long> tiposActividadIds, List<Long> tiposEquipamientoIds) {
        actividad.setTitulo(titulo.trim());
        actividad.setDescripcion(descripcion.trim());
        actividad.setDepartamento(departamento);
        actividad.setLocalidad(vacio(localidad) ? null : localidad.trim());
        actividad.setDuracionMinutos(duracionMinutos);
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
