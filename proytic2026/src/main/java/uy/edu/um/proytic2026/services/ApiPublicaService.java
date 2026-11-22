package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.ActividadPublicaDto;
import uy.edu.um.proytic2026.dto.EmprendimientoPublicoDto;
import uy.edu.um.proytic2026.dto.HorarioPublicoDto;
import uy.edu.um.proytic2026.dto.VerificacionDto;
import uy.edu.um.proytic2026.entities.Actividad;
import uy.edu.um.proytic2026.entities.Emprendimiento;
import uy.edu.um.proytic2026.entities.EstadoEmprendimiento;
import uy.edu.um.proytic2026.entities.Horario;
import uy.edu.um.proytic2026.repository.ActividadRepository;
import uy.edu.um.proytic2026.repository.EmprendimientoRepository;
import uy.edu.um.proytic2026.repository.ResenaRepository;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Reglas de la API publica que UMnatural expone a otras plataformas.
// Solo lectura, y solo se publica lo que un administrador ya aprobó (RNE #8).
@Service
@Transactional(readOnly = true)
public class ApiPublicaService {

    @Autowired
    private EmprendimientoRepository emprendimientoRepository;

    @Autowired
    private ActividadRepository actividadRepository;

    // Reutiliza la regla de publicacion del portal (emprendimiento aceptado y actividad no dada de baja)
    @Autowired
    private CatalogoService catalogoService;

    @Autowired
    private ResenaService resenaService;

    @Autowired
    private ResenaRepository resenaRepository;

    // 1) Listado de todos los emprendimientos (solo los ACEPTADOS)
    public List<EmprendimientoPublicoDto> listarEmprendimientos() {
        return emprendimientoRepository
                .findByEstadoOrderByNombreAsc(EstadoEmprendimiento.ACEPTADO) //Le pide al repository los emprendimientos con estado ACEPTADO, ordenado por nombre.
                .stream() //convierte la lista en un flujo de emprendimientos.
                .map(this::aDto) //aplica el metodo aDto a cada emprendimiento, transformándolo en un DTO.
                .toList(); //junta los resultados en una lista nueva de DTOs.
    }

    // Convierte la entidad en el DTO público (solo los campos que se pueden mostrar)
    private EmprendimientoPublicoDto aDto(Emprendimiento e) {
        return new EmprendimientoPublicoDto(
                e.getId(), e.getNombre(), e.getTelefono(),
                e.getEmail(), e.getUbicacion(), e.getDescripcion());
    }

    // 2) Todas las actividades que se hacen un dia en cierto departamento y/o localidad.
    // Hay que indicar al menos uno de los dos; si vienen los dos, tienen que coincidir ambos.
    public List<ActividadPublicaDto> actividadesDelDia(LocalDate fecha, String departamento, String localidad) {
        if (fecha == null) {
            throw new IllegalArgumentException("Falta la fecha (formato AAAA-MM-DD)");
        }
        if (estaVacio(departamento) && estaVacio(localidad)) {
            throw new IllegalArgumentException("Indique un departamento o una localidad");
        }

        Map<Long, double[]> promedios = resenaService.promedios();
        List<ActividadPublicaDto> resultado = new ArrayList<>();

        for (Actividad actividad : actividadRepository.findAll()) {
            if (!catalogoService.estaPublicada(actividad)) continue;
            if (!coincide(actividad.getDepartamento(), departamento)) continue;
            if (!coincide(actividad.getLocalidad(), localidad)) continue;

            List<HorarioPublicoDto> horarios = horariosDelDia(actividad, fecha);
            if (horarios.isEmpty()) continue;   // ese dia no se hace

            resultado.add(aDto(actividad, horarios, promedios));
        }

        // La que antes empieza, primero
        resultado.sort(Comparator.comparing((ActividadPublicaDto a) -> a.horarios().get(0).horaInicio()));
        return resultado;
    }

    // 3) Top 3 de actividades mejor puntuadas de un departamento en un mes
    // (si no se indica el mes, es el mes actual). Cuentan solo las valoraciones visibles
    // hechas ese mes; una actividad sin valoraciones ese mes no entra al ranking.
    public List<ActividadPublicaDto> top3DelMes(String departamento, YearMonth mes) {
        if (estaVacio(departamento)) {
            throw new IllegalArgumentException("Indique un departamento");
        }

        YearMonth mesConsultado = (mes == null) ? YearMonth.now() : mes;
        LocalDateTime desde = mesConsultado.atDay(1).atStartOfDay();
        LocalDateTime hasta = mesConsultado.plusMonths(1).atDay(1).atStartOfDay();

        // actividadId -> [promedio del mes, cantidad de valoraciones del mes]
        Map<Long, double[]> promediosDelMes = new HashMap<>();
        for (Object[] fila : resenaRepository.promediosEntre(desde, hasta)) {
            promediosDelMes.put((Long) fila[0],
                    new double[]{((Number) fila[1]).doubleValue(), ((Number) fila[2]).doubleValue()});
        }

        List<Actividad> candidatas = new ArrayList<>();
        for (Actividad actividad : actividadRepository.findAll()) {
            if (!catalogoService.estaPublicada(actividad)) continue;
            if (!coincide(actividad.getDepartamento(), departamento)) continue;
            if (!promediosDelMes.containsKey(actividad.getId())) continue;   // sin valoraciones ese mes
            candidatas.add(actividad);
        }

        // Mejor promedio primero; si empatan, la que tiene mas valoraciones; si siguen empatadas, la mas vieja
        candidatas.sort((a, b) -> {
            double[] va = promediosDelMes.get(a.getId());
            double[] vb = promediosDelMes.get(b.getId());
            int porPromedio = Double.compare(vb[0], va[0]);
            if (porPromedio != 0) return porPromedio;
            int porCantidad = Double.compare(vb[1], va[1]);
            if (porCantidad != 0) return porCantidad;
            return Long.compare(a.getId(), b.getId());
        });

        return candidatas.stream()
                .limit(3)
                .map(a -> aDto(a, List.of(), promediosDelMes))   // sin horarios: es un ranking
                .toList();
    }

    // 4) Verificar emprendimiento: dice si existe y si esta ACEPTADO por la administracion.
    // Si no existe no es un error: se responde existe=false.
    public VerificacionDto verificarEmprendimiento(Long id) {
        return emprendimientoRepository.findById(id)
                .map(e -> {
                    boolean verificado = e.getEstado() == EstadoEmprendimiento.ACEPTADO;
                    return new VerificacionDto(e.getId(), true, verificado, verificado ? e.getNombre() : null);
                })
                .orElse(new VerificacionDto(id, false, false, null));
    }

    // 5) Buscar actividades por palabra clave. Reutiliza la busqueda del portal: mira titulo, descripcion,
    // emprendimiento, departamento, localidad y categorias, sin distinguir mayusculas ni tildes,
    // y solo entre las actividades publicadas. Cada una trae sus proximos horarios con lugar.
    public List<ActividadPublicaDto> buscarActividades(String palabraClave) {
        if (estaVacio(palabraClave)) {
            throw new IllegalArgumentException("Indique una palabra clave");
        }

        Map<Long, double[]> promedios = resenaService.promedios();

        return catalogoService.buscar(palabraClave, null, null, null, null).stream()
                .map(a -> aDto(a, horariosProximos(a), promedios))
                .toList();
    }

    // Proximos horarios con cupos y dentro del plazo para reservar (regla de CatalogoService)
    private List<HorarioPublicoDto> horariosProximos(Actividad actividad) {
        return catalogoService.proximosHorarios(actividad).stream()
                .map(this::aHorarioDto)
                .toList();
    }

    // Horarios de la actividad en esa fecha, sin los cancelados ni los eliminados
    private List<HorarioPublicoDto> horariosDelDia(Actividad actividad, LocalDate fecha) {
        return actividad.getHorarios().stream()
                .filter(Horario::disponible)
                .filter(h -> fecha.equals(h.getFecha()))
                .sorted(Comparator.comparing(Horario::getHoraInicio))
                .map(this::aHorarioDto)
                .toList();
    }

    // Convierte el horario en el DTO publico
    private HorarioPublicoDto aHorarioDto(Horario h) {
        return new HorarioPublicoDto(h.getFecha(), h.getHoraInicio(), h.getHoraFin(), h.getCuposDisponibles());
    }

    // Convierte la actividad en el DTO publico (solo los campos que se pueden mostrar)
    private ActividadPublicaDto aDto(Actividad actividad, List<HorarioPublicoDto> horarios, Map<Long, double[]> promedios) {
        double[] valoracion = promedios.get(actividad.getId());   // [promedio, cantidad] o null
        Double promedio = valoracion == null ? null : Math.round(valoracion[0] * 10) / 10.0;
        Long cantidad = valoracion == null ? 0L : (long) valoracion[1];

        return new ActividadPublicaDto(
                actividad.getId(), actividad.getTitulo(), actividad.getDescripcion(),
                actividad.getDepartamento(), actividad.getLocalidad(), actividad.getPrecio(),
                actividad.getEmprendimiento().getId(), actividad.getEmprendimiento().getNombre(),
                promedio, cantidad, horarios);
    }

    // Si no se indico el filtro, no filtra. Si se indico, compara sin mayusculas ni tildes.
    private boolean coincide(String valorDeLaActividad, String filtro) {
        if (estaVacio(filtro)) return true;
        return valorDeLaActividad != null && normalizar(valorDeLaActividad).equals(normalizar(filtro));
    }

    private boolean estaVacio(String texto) {
        return texto == null || texto.isBlank();
    }

    // "Paysandu", "paysandú" y "PAYSANDÚ" quedan iguales
    private String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).trim();
    }
}
