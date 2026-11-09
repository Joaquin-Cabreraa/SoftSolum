package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.entities.TipoActividad;
import uy.edu.um.proytic2026.entities.TipoEquipamiento;
import uy.edu.um.proytic2026.repository.ActividadRepository;
import uy.edu.um.proytic2026.repository.TipoActividadRepository;
import uy.edu.um.proytic2026.repository.TipoEquipamientoRepository;

import java.util.List;

// Datos maestros que mantiene el administrador (CU-A05):
// alta, consulta, modificación y eliminación de tipos de actividad y de equipamiento.
// Todos los métodos que modifican devuelven el mensaje de error, o null si salió bien.
@Service
public class TiposService {

    @Autowired private TipoActividadRepository tipoActividadRepository;
    @Autowired private TipoEquipamientoRepository tipoEquipamientoRepository;
    @Autowired private ActividadRepository actividadRepository;

    // ---------- Tipos de actividad ----------

    public List<TipoActividad> tiposDeActividad() {
        return tipoActividadRepository.findAllByOrderByNombreAsc();
    }

    @Transactional
    public String crearTipoActividad(String nombre) {
        String error = validarNombre(nombre, tipoActividadRepository.existsByNombreIgnoreCase(limpiar(nombre)));
        if (error != null) return error;

        TipoActividad tipo = new TipoActividad();
        tipo.setNombre(limpiar(nombre));
        tipoActividadRepository.save(tipo);
        return null;
    }

    @Transactional
    public String renombrarTipoActividad(Long id, String nombre) {
        TipoActividad tipo = tipoActividadRepository.findById(id).orElse(null);
        if (tipo == null) return "El tipo de actividad no existe.";

        boolean mismoNombre = tipo.getNombre().equalsIgnoreCase(limpiar(nombre));
        String error = validarNombre(nombre, !mismoNombre && tipoActividadRepository.existsByNombreIgnoreCase(limpiar(nombre)));
        if (error != null) return error;

        tipo.setNombre(limpiar(nombre));
        tipoActividadRepository.save(tipo);
        return null;
    }

    // Es un dato global, compartido por actividades de muchos emprendimientos:
    // si alguna lo usa, no se puede borrar.
    @Transactional
    public String eliminarTipoActividad(Long id) {
        if (!tipoActividadRepository.existsById(id)) return "El tipo de actividad no existe.";
        if (actividadRepository.existsByTipoActividad_Id(id)) {
            return "No se puede eliminar: hay actividades que usan este tipo.";
        }
        tipoActividadRepository.deleteById(id);
        return null;
    }

    // ---------- Tipos de equipamiento ----------

    public List<TipoEquipamiento> tiposDeEquipamiento() {
        return tipoEquipamientoRepository.findAllByOrderByNombreAsc();
    }

    @Transactional
    public String crearTipoEquipamiento(String nombre) {
        String error = validarNombre(nombre, tipoEquipamientoRepository.existsByNombreIgnoreCase(limpiar(nombre)));
        if (error != null) return error;

        TipoEquipamiento tipo = new TipoEquipamiento();
        tipo.setNombre(limpiar(nombre));
        tipoEquipamientoRepository.save(tipo);
        return null;
    }

    @Transactional
    public String renombrarTipoEquipamiento(Long id, String nombre) {
        TipoEquipamiento tipo = tipoEquipamientoRepository.findById(id).orElse(null);
        if (tipo == null) return "El tipo de equipamiento no existe.";

        boolean mismoNombre = tipo.getNombre().equalsIgnoreCase(limpiar(nombre));
        String error = validarNombre(nombre, !mismoNombre && tipoEquipamientoRepository.existsByNombreIgnoreCase(limpiar(nombre)));
        if (error != null) return error;

        tipo.setNombre(limpiar(nombre));
        tipoEquipamientoRepository.save(tipo);
        return null;
    }

    @Transactional
    public String eliminarTipoEquipamiento(Long id) {
        if (!tipoEquipamientoRepository.existsById(id)) return "El tipo de equipamiento no existe.";
        if (actividadRepository.existsByTiposEquipamiento_Id(id)) {
            return "No se puede eliminar: hay actividades que usan este equipamiento.";
        }
        tipoEquipamientoRepository.deleteById(id);
        return null;
    }

    // ---------- Tipos nuevos escritos por el prestador ----------

    // El prestador puede escribir tipos que no están en la lista (separados por coma).
    // Si ya existe uno con ese nombre (sin importar mayúsculas) se usa ese; si no, se crea.
    // Quedan como datos globales: el administrador después los puede renombrar o borrar.
    // Devuelve los ids, para sumarlos a los que marcó en las casillas.
    @Transactional
    public List<Long> obtenerOCrearTiposActividad(String texto) {
        List<Long> ids = new java.util.ArrayList<>();
        for (String nombre : separar(texto)) {
            TipoActividad tipo = tipoActividadRepository.findFirstByNombreIgnoreCase(nombre).orElse(null);
            if (tipo == null) {
                tipo = new TipoActividad();
                tipo.setNombre(nombre);
                tipo = tipoActividadRepository.save(tipo);
            }
            ids.add(tipo.getId());
        }
        return ids;
    }

    @Transactional
    public List<Long> obtenerOCrearTiposEquipamiento(String texto) {
        List<Long> ids = new java.util.ArrayList<>();
        for (String nombre : separar(texto)) {
            TipoEquipamiento tipo = tipoEquipamientoRepository.findFirstByNombreIgnoreCase(nombre).orElse(null);
            if (tipo == null) {
                tipo = new TipoEquipamiento();
                tipo.setNombre(nombre);
                tipo = tipoEquipamientoRepository.save(tipo);
            }
            ids.add(tipo.getId());
        }
        return ids;
    }

    // "Linterna, carpa ,  " -> ["Linterna", "carpa"] (sin vacíos, máximo 60 caracteres cada uno)
    private List<String> separar(String texto) {
        List<String> nombres = new java.util.ArrayList<>();
        if (texto == null) return nombres;
        for (String parte : texto.split(",")) {
            String nombre = parte.trim();
            if (!nombre.isEmpty() && nombre.length() <= 60) nombres.add(nombre);
        }
        return nombres;
    }

    // ---------- Ayuda ----------

    private String validarNombre(String nombre, boolean yaExiste) {
        if (nombre == null || nombre.isBlank()) return "Escribí un nombre.";
        if (limpiar(nombre).length() > 60) return "El nombre puede tener como máximo 60 caracteres.";
        if (yaExiste) return "Ya existe uno con ese nombre.";
        return null;
    }

    private String limpiar(String nombre) {
        return nombre == null ? "" : nombre.trim();
    }
}
