package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.repository.ActividadRepository;
import uy.edu.um.proytic2026.repository.HorarioRepository;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

// Lo que ve el público en el portal: explorar, buscar y ver el detalle de actividades.
// (CU-T04 Buscar y explorar actividades, CU-T05 Ver detalle y horarios disponibles)
@Service
@Transactional(readOnly = true)
public class CatalogoService {

    @Autowired
    private ActividadRepository actividadRepository;

    @Autowired
    private HorarioRepository horarioRepository;

    // ---------- Reglas de publicación ----------

    // Una actividad se muestra al público solo si su emprendimiento fue ACEPTADO
    // por un administrador (RNE #8) y no está dada de baja (RNE #9).
    public boolean estaPublicada(Actividad actividad) {
        if (actividad == null || actividad.getEmprendimiento() == null) {
            return false;
        }
        boolean emprendimientoAceptado =
                actividad.getEmprendimiento().getEstado() == EstadoEmprendimiento.ACEPTADO;
        boolean dadaDeBaja = "BAJA".equalsIgnoreCase(actividad.getEstado());
        return emprendimientoAceptado && !dadaDeBaja;
    }

    // ¿Todavía se puede reservar este horario? La actividad define cuántos minutos
    // antes del inicio se cierra la reserva. Si son 0, el límite es la hora de inicio.
    public boolean sePuedeReservar(Horario horario, LocalDateTime ahora) {
        LocalDateTime inicio = horario.getFecha().atTime(horario.getHoraInicio());
        Integer minutos = horario.getActividad().getMinPreviosReservar();
        LocalDateTime limite = inicio.minusMinutes(minutos == null ? 0 : minutos);
        return ahora.isBefore(limite);
    }

    // ---------- Explorar / buscar ----------

    // Busca entre las actividades publicadas. Todos los filtros son opcionales
    // (si vienen vacíos, no filtran).
    public List<Actividad> buscar(String texto, String departamento, Long tipoActividadId,
                                  LocalDate fecha, BigDecimal precioMaximo) {
        List<Actividad> resultado = new ArrayList<>();

        for (Actividad actividad : actividadRepository.findAll()) {
            if (!estaPublicada(actividad)) continue;
            if (!coincideTexto(actividad, texto)) continue;
            if (!coincideDepartamento(actividad, departamento)) continue;
            if (!coincideTipo(actividad, tipoActividadId)) continue;
            if (precioMaximo != null && actividad.getPrecio().compareTo(precioMaximo) > 0) continue;
            if (fecha != null && !tieneHorarioDisponibleEn(actividad, fecha)) continue;

            resultado.add(actividad);
        }

        resultado.sort(Comparator.comparing(Actividad::getId).reversed());   // las más nuevas primero
        return resultado;
    }

    // Busca la palabra clave en título, descripción, nombre del emprendimiento,
    // departamento, localidad y categorías. No distingue mayúsculas ni tildes.
    private boolean coincideTexto(Actividad actividad, String texto) {
        if (texto == null || texto.isBlank()) return true;

        String buscado = normalizar(texto);
        List<String> campos = new ArrayList<>();
        campos.add(actividad.getTitulo());
        campos.add(actividad.getDescripcion());
        campos.add(actividad.getEmprendimiento().getNombre());
        campos.add(actividad.getDepartamento());
        campos.add(actividad.getLocalidad());
        for (TipoActividad tipo : actividad.getTipoActividad()) {
            campos.add(tipo.getNombre());
        }

        for (String campo : campos) {
            if (campo != null && normalizar(campo).contains(buscado)) {
                return true;
            }
        }
        return false;
    }

    private boolean coincideDepartamento(Actividad actividad, String departamento) {
        if (departamento == null || departamento.isBlank()) return true;
        return actividad.getDepartamento() != null
                && normalizar(actividad.getDepartamento()).equals(normalizar(departamento));
    }

    private boolean coincideTipo(Actividad actividad, Long tipoActividadId) {
        if (tipoActividadId == null) return true;
        for (TipoActividad tipo : actividad.getTipoActividad()) {
            if (tipo.getId().equals(tipoActividadId)) return true;
        }
        return false;
    }

    private boolean tieneHorarioDisponibleEn(Actividad actividad, LocalDate fecha) {
        for (Horario horario : proximosHorarios(actividad)) {
            if (horario.getFecha().equals(fecha)) return true;
        }
        return false;
    }

    // Pasa a minúsculas y saca las tildes: "Colonia" y "colónia" quedan iguales.
    private String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).trim();
    }

    // ---------- Opciones para los filtros ----------

    // Departamentos que tienen al menos una actividad publicada (para el desplegable)
    public List<String> departamentosConActividades() {
        Set<String> departamentos = new TreeSet<>();
        for (Actividad actividad : actividadRepository.findAll()) {
            if (estaPublicada(actividad) && actividad.getDepartamento() != null
                    && !actividad.getDepartamento().isBlank()) {
                departamentos.add(actividad.getDepartamento());
            }
        }
        return new ArrayList<>(departamentos);
    }

    // Tipos de actividad usados por actividades publicadas (para el desplegable)
    public List<TipoActividad> tiposConActividades() {
        Map<Long, TipoActividad> tipos = new TreeMap<>();
        for (Actividad actividad : actividadRepository.findAll()) {
            if (!estaPublicada(actividad)) continue;
            for (TipoActividad tipo : actividad.getTipoActividad()) {
                tipos.put(tipo.getId(), tipo);
            }
        }
        List<TipoActividad> lista = new ArrayList<>(tipos.values());
        lista.sort(Comparator.comparing(TipoActividad::getNombre));
        return lista;
    }

    // ---------- Detalle ----------

    // Devuelve la actividad solo si está publicada; si no, null (la página muestra 404).
    public Actividad obtenerActividadPublicada(Long id) {
        Actividad actividad = actividadRepository.findById(id).orElse(null);
        return estaPublicada(actividad) ? actividad : null;
    }

    // Próximas oportunidades para hacer la actividad: horarios futuros,
    // con cupos y que todavía estén dentro del plazo para reservar.
    public List<Horario> proximosHorarios(Actividad actividad) {
        LocalDateTime ahora = LocalDateTime.now();
        List<Horario> disponibles = new ArrayList<>();

        for (Horario horario : horarioRepository
                .findByActividadIdAndFechaGreaterThanEqualOrderByFechaAscHoraInicioAsc(actividad.getId(), ahora.toLocalDate())) {
            if (horario.getCuposDisponibles() > 0 && sePuedeReservar(horario, ahora)) {
                disponibles.add(horario);
            }
        }
        return disponibles;
    }
}
