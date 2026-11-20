package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uy.edu.um.proytic2026.entities.Emprendimiento;
import uy.edu.um.proytic2026.entities.Empresa;
import uy.edu.um.proytic2026.entities.EstadoEmprendimiento;
import uy.edu.um.proytic2026.entities.Particular;
import uy.edu.um.proytic2026.entities.Prestador;
import uy.edu.um.proytic2026.repository.EmprendimientoRepository;
import uy.edu.um.proytic2026.repository.PrestadorRepository;
import uy.edu.um.proytic2026.repository.ActividadRepository;
import uy.edu.um.proytic2026.entities.Actividad;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdministradorService {

    @Autowired
    private EmprendimientoRepository emprendimientoRepository;

    @Autowired
    private PrestadorRepository prestadorRepository;   // NUEVO

    @Autowired
    private ActividadRepository actividadRepository;

    public boolean aceptarEmprendimiento(Long id) {
        Emprendimiento e = emprendimientoRepository.findById(id).orElse(null);

        if (e == null || e.getEstado() != EstadoEmprendimiento.PENDIENTE) {
            return false;
        }

        e.setEstado(EstadoEmprendimiento.ACEPTADO);
        emprendimientoRepository.save(e);
        return true;
    }

    public boolean denegarEmprendimiento(Long id) {
        Emprendimiento e = emprendimientoRepository.findById(id).orElse(null);

        if (e == null || e.getEstado() != EstadoEmprendimiento.PENDIENTE) {
            return false;
        }

        e.setEstado(EstadoEmprendimiento.DENEGADO);
        emprendimientoRepository.save(e);
        return true;
    }

    // Devuelve los pendientes con los datos del emprendimiento y de su dueño
    public List<Map<String, Object>> listarPendientes() {

        List<Map<String, Object>> pendientes = new ArrayList<>();

        for (Emprendimiento e : emprendimientoRepository.findAll()) {

            if (e.getEstado() != EstadoEmprendimiento.PENDIENTE) {
                continue;
            }

            Map<String, Object> datos = new HashMap<>();

            // Datos del emprendimiento
            datos.put("id", e.getId());
            datos.put("nombre", e.getNombre());
            datos.put("ubicacion", e.getUbicacion());
            datos.put("descripcion", e.getDescripcion());

            // Datos del dueño (empresa o particular)
            Prestador p = prestadorRepository.findByEmprendimiento(e).orElse(null);

            if (p != null) {
                datos.put("email", p.getUserEmail());
                datos.put("telefono", p.getTelefono());

                if (p instanceof Empresa empresa) {
                    datos.put("tipo", "Empresa");
                    datos.put("razonSocial", empresa.getName());
                    datos.put("rut", empresa.getRut());
                } else if (p instanceof Particular particular) {
                    datos.put("tipo", "Particular");
                    datos.put("responsable", particular.getName() + " " + particular.getLastName());
                    datos.put("documento", particular.getDocumento());
                }
            }

            pendientes.add(datos);
        }

        return pendientes;
    }

    // ================== Gestión completa (panel del administrador) ==================

    // Todos los emprendimientos (o solo los de un estado), con los datos del dueño
    // y cuántas actividades tienen. Los más nuevos primero.
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarEmprendimientos(EstadoEmprendimiento filtro) {
        List<Emprendimiento> todos = new ArrayList<>(emprendimientoRepository.findAll());
        todos.sort(Comparator.comparing(Emprendimiento::getId).reversed());

        List<Map<String, Object>> lista = new ArrayList<>();
        for (Emprendimiento e : todos) {
            if (filtro != null && e.getEstado() != filtro) continue;
            Map<String, Object> datos = new HashMap<>();
            datos.put("id", e.getId());
            datos.put("nombre", e.getNombre());
            datos.put("estado", e.getEstado().name());
            datos.put("ubicacion", e.getUbicacion());
            datos.put("descripcion", e.getDescripcion());
            datos.put("emailEmprendimiento", e.getEmail());
            datos.put("telefonoEmprendimiento", e.getTelefono());
            datos.put("actividades", actividadRepository.findByEmprendimientoIdOrderByIdDesc(e.getId()).stream()
                    .filter(a -> !"ELIMINADA".equals(a.getEstado())).count());

            Prestador p = prestadorRepository.findByEmprendimiento(e).orElse(null);
            if (p != null) {
                datos.put("email", p.getUserEmail());
                datos.put("telefono", p.getTelefono());
                if (p instanceof Empresa empresa) {
                    datos.put("tipo", "Empresa");
                    datos.put("titular", empresa.getName());
                    datos.put("identificacion", "RUT " + empresa.getRut());
                } else if (p instanceof Particular particular) {
                    datos.put("tipo", "Particular");
                    datos.put("titular", particular.getName() + " " + (particular.getLastName() == null ? "" : particular.getLastName()));
                    datos.put("identificacion", "CI " + particular.getDocumento());
                }
            }
            lista.add(datos);
        }
        return lista;
    }

    // Cambia la aprobación de un emprendimiento a cualquier estado (también uno ya aceptado).
    // Si un emprendimiento aceptado pasa a DENEGADO o PENDIENTE, sus actividades dejan de
    // verse en el portal (RNE #8); las reservas ya hechas se conservan.
    // Devuelve el mensaje de error, o null si se cambió.
    @Transactional
    public String cambiarEstadoEmprendimiento(Long id, EstadoEmprendimiento nuevo) {
        Emprendimiento e = emprendimientoRepository.findById(id).orElse(null);
        if (e == null) return "El emprendimiento no existe.";
        if (nuevo == null) return "Estado no válido.";
        if (e.getEstado() == nuevo) return "El emprendimiento ya estaba en ese estado.";
        e.setEstado(nuevo);
        emprendimientoRepository.save(e);
        return null;
    }

    // Todas las actividades del portal (de todos los emprendimientos), con filtro opcional
    // por texto (título o emprendimiento) y por estado (ACTIVA / BAJA).
    @Transactional(readOnly = true)
    public List<Actividad> listarActividades(String texto, String estado) {
        String buscado = texto == null || texto.isBlank() ? null : normalizar(texto);
        List<Actividad> lista = new ArrayList<>();
        for (Actividad a : actividadRepository.findAll()) {
            if ("ELIMINADA".equals(a.getEstado())) continue;   // la eliminó el prestador
            if (estado != null && !estado.isBlank() && !estado.equalsIgnoreCase(a.getEstado())) continue;
            if (buscado != null) {
                String donde = normalizar((a.getTitulo() == null ? "" : a.getTitulo()) + " "
                        + a.getEmprendimiento().getNombre() + " " + (a.getDepartamento() == null ? "" : a.getDepartamento()));
                if (!donde.contains(buscado)) continue;
            }
            a.getHorarios().size();   // se cargan para mostrar cuántos tiene
            lista.add(a);
        }
        lista.sort(Comparator.comparing(Actividad::getId).reversed());
        return lista;
    }

    // El administrador da de baja una actividad (ej. contenido inapropiado) o la vuelve a activar.
    // Es una baja lógica: no se borra nada y las reservas se conservan (RNE #9).
    @Transactional
    public String cambiarEstadoActividad(Long id, String nuevoEstado) {
        return cambiarEstadoActividad(id, nuevoEstado, null, null);
    }

    // Al quitarla, el administrador tiene que explicar por qué y dejar un correo de contacto:
    // el prestador los ve en "Mis actividades" para poder aclarar la situación.
    public String cambiarEstadoActividad(Long id, String nuevoEstado, String motivo, String contacto) {
        Actividad a = actividadRepository.findById(id).orElse(null);
        if (a == null) return "La actividad no existe.";
        if (!"ACTIVA".equals(nuevoEstado) && !"BAJA".equals(nuevoEstado)) return "Estado no válido.";
        boolean yaEstaba = nuevoEstado.equals(a.getEstado());
        // Se permite quitar una actividad que el prestador había pausado (así él no la puede republicar)
        boolean quitarUnaPausada = yaEstaba && "BAJA".equals(nuevoEstado) && !a.quitadaPorAdministrador();
        if (yaEstaba && !quitarUnaPausada) {
            return "La actividad ya estaba en ese estado.";
        }
        if ("BAJA".equals(nuevoEstado)) {
            if (motivo == null || motivo.isBlank()) {
                return "Escribí el motivo: el prestador lo va a ver.";
            }
            if (contacto == null || !contacto.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                return "Dejá un correo válido para que el prestador te pueda contactar.";
            }
            a.setMotivoBajaAdmin(motivo.trim().length() > 500 ? motivo.trim().substring(0, 500) : motivo.trim());
            a.setContactoBajaAdmin(contacto.trim().toLowerCase());
            a.setFechaBajaAdmin(java.time.LocalDateTime.now());
            a.setBajaPorAdministrador(true);   // el prestador no la puede volver a publicar por su cuenta
        } else {
            a.setBajaPorAdministrador(false);
            a.setMotivoBajaAdmin(null);
            a.setContactoBajaAdmin(null);
            a.setFechaBajaAdmin(null);
        }
        a.setEstado(nuevoEstado);
        actividadRepository.save(a);
        return null;
    }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim();
    }
}
