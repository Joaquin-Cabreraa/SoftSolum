package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uy.edu.um.proytic2026.entities.Actividad;
import uy.edu.um.proytic2026.entities.Administrador;
import uy.edu.um.proytic2026.entities.Emprendimiento;
import uy.edu.um.proytic2026.entities.Empresa;
import uy.edu.um.proytic2026.entities.EstadoEmprendimiento;
import uy.edu.um.proytic2026.entities.Particular;
import uy.edu.um.proytic2026.entities.Prestador;
import uy.edu.um.proytic2026.entities.VerificacionEmprendimiento;

import uy.edu.um.proytic2026.repository.ActividadRepository;
import uy.edu.um.proytic2026.repository.AdministradorRepository;
import uy.edu.um.proytic2026.repository.EmprendimientoRepository;
import uy.edu.um.proytic2026.repository.PrestadorRepository;
import uy.edu.um.proytic2026.repository.VerificacionEmprendimientoRepository;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdministradorService {

    @Autowired
    private EmprendimientoRepository emprendimientoRepository;

    @Autowired
    private PrestadorRepository prestadorRepository;

    @Autowired
    private ActividadRepository actividadRepository;

    @Autowired
    private AdministradorRepository administradorRepository;

    @Autowired
    private VerificacionEmprendimientoRepository verificacionEmprendimientoRepository;

    // Devuelve los emprendimientos pendientes con los datos del dueño.

    public List<Map<String, Object>> listarPendientes() {

        List<Map<String, Object>> pendientes = new ArrayList<>();

        for (Emprendimiento e : emprendimientoRepository.findAll()) {

            if (e.getEstado() != EstadoEmprendimiento.PENDIENTE) {
                continue;
            }

            Map<String, Object> datos = new HashMap<>();

            datos.put("id", e.getId());
            datos.put("nombre", e.getNombre());
            datos.put("ubicacion", e.getUbicacion());
            datos.put("descripcion", e.getDescripcion());

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
                    datos.put(
                            "responsable",
                            particular.getName() + " " + particular.getLastName()
                    );
                    datos.put("documento", particular.getDocumento());
                }
            }

            pendientes.add(datos);
        }

        return pendientes;
    }

    // Gestión completa del panel de administrador.

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarEmprendimientos(
            EstadoEmprendimiento filtro) {

        List<Emprendimiento> todos =
                new ArrayList<>(emprendimientoRepository.findAll());

        todos.sort(
                Comparator.comparing(Emprendimiento::getId).reversed()
        );

        List<Map<String, Object>> lista = new ArrayList<>();

        for (Emprendimiento e : todos) {

            if (filtro != null && e.getEstado() != filtro) {
                continue;
            }

            Map<String, Object> datos = new HashMap<>();

            datos.put("id", e.getId());
            datos.put("nombre", e.getNombre());
            datos.put("estado", e.getEstado().name());
            datos.put("ubicacion", e.getUbicacion());
            datos.put("descripcion", e.getDescripcion());
            datos.put("emailEmprendimiento", e.getEmail());
            datos.put("telefonoEmprendimiento", e.getTelefono());

            datos.put(
                    "actividades",
                    actividadRepository
                            .findByEmprendimientoIdOrderByIdDesc(e.getId())
                            .stream()
                            .filter(a -> !"ELIMINADA".equals(a.getEstado()))
                            .count()
            );

            Prestador p =
                    prestadorRepository.findByEmprendimiento(e).orElse(null);

            if (p != null) {

                datos.put("email", p.getUserEmail());
                datos.put("telefono", p.getTelefono());

                if (p instanceof Empresa empresa) {
                    datos.put("tipo", "Empresa");
                    datos.put("titular", empresa.getName());
                    datos.put("identificacion", "RUT " + empresa.getRut());

                } else if (p instanceof Particular particular) {
                    datos.put("tipo", "Particular");
                    datos.put(
                            "titular",
                            particular.getName() + " " +
                                    (particular.getLastName() == null
                                            ? ""
                                            : particular.getLastName())
                    );
                    datos.put(
                            "identificacion",
                            "CI " + particular.getDocumento()
                    );
                }
            }

            lista.add(datos);
        }

        return lista;
    }

    // Cambia el estado del emprendimiento y registra el historial.
    // Devuelve null si salió bien o un mensaje si hubo un error.

    @Transactional
    public String cambiarEstadoEmprendimiento(
            Long id,
            EstadoEmprendimiento nuevo,
            Long administradorId,
            String motivo) {

        Emprendimiento e =
                emprendimientoRepository.findById(id).orElse(null);

        if (e == null) {
            return "El emprendimiento no existe.";
        }

        if (nuevo == null) {
            return "Estado no válido.";
        }

        if (e.getEstado() == nuevo) {
            return "El emprendimiento ya estaba en ese estado.";
        }

        if (administradorId == null) {
            return "No se identificó al administrador.";
        }

        Administrador administrador =
                administradorRepository.findById(administradorId).orElse(null);

        if (administrador == null) {
            return "El administrador no existe.";
        }

        if (nuevo == EstadoEmprendimiento.DENEGADO) {

            if (motivo == null || motivo.isBlank()) {
                return "Tenés que indicar el motivo del rechazo.";
            }

            if (motivo.trim().length() > 500) {
                return "El motivo no puede superar los 500 caracteres.";
            }
        }

        // Guardamos el estado anterior antes de modificarlo.
        EstadoEmprendimiento estadoAnterior = e.getEstado();

        LocalDateTime fecha = LocalDateTime.now();

        String motivoRegistrado =
                nuevo == EstadoEmprendimiento.DENEGADO
                        ? motivo.trim()
                        : null;

        // Actualizamos los datos actuales del emprendimiento.

        e.setMotivoRechazo(motivoRegistrado);
        e.setAdministradorVerificador(administrador);
        e.setFechaVerificacion(fecha);
        e.setEstado(nuevo);

        emprendimientoRepository.save(e);

        // Creamos una entrada nueva en el historial.

        VerificacionEmprendimiento registro =
                new VerificacionEmprendimiento();

        registro.setEmprendimiento(e);
        registro.setAdministrador(administrador);
        registro.setEstadoAnterior(estadoAnterior);
        registro.setEstadoNuevo(nuevo);
        registro.setFecha(fecha);
        registro.setMotivo(motivoRegistrado);

        verificacionEmprendimientoRepository.save(registro);

        return null;
    }

    // Consulta del historial de un emprendimiento.

    @Transactional(readOnly = true)
    public List<VerificacionEmprendimiento> obtenerHistorial(
            Long emprendimientoId) {

        return verificacionEmprendimientoRepository
                .findByEmprendimientoIdOrderByFechaDesc(emprendimientoId);
    }

    // Consulta de todas las verificaciones.

    @Transactional(readOnly = true)
    public List<VerificacionEmprendimiento> obtenerHistorialCompleto() {

        return verificacionEmprendimientoRepository
                .findAllByOrderByFechaDesc();
    }

    // Gestión de actividades del portal.

    @Transactional(readOnly = true)
    public List<Actividad> listarActividades(
            String texto,
            String estado) {

        String buscado =
                texto == null || texto.isBlank()
                        ? null
                        : normalizar(texto);

        List<Actividad> lista = new ArrayList<>();

        for (Actividad a : actividadRepository.findAll()) {

            if ("ELIMINADA".equals(a.getEstado())) {
                continue;
            }

            if (estado != null
                    && !estado.isBlank()
                    && !estado.equalsIgnoreCase(a.getEstado())) {
                continue;
            }

            if (buscado != null) {

                String donde = normalizar(
                        (a.getTitulo() == null ? "" : a.getTitulo())
                                + " "
                                + a.getEmprendimiento().getNombre()
                                + " "
                                + (a.getDepartamento() == null
                                ? ""
                                : a.getDepartamento())
                );

                if (!donde.contains(buscado)) {
                    continue;
                }
            }

            a.getHorarios().size();
            lista.add(a);
        }

        lista.sort(
                Comparator.comparing(Actividad::getId).reversed()
        );

        return lista;
    }

    // Cambiar el estado de una actividad.

    @Transactional
    public String cambiarEstadoActividad(
            Long id,
            String nuevoEstado) {

        return cambiarEstadoActividad(
                id,
                nuevoEstado,
                null,
                null
        );
    }

    // Baja o reactivación de actividades por el administrador.

    @Transactional
    public String cambiarEstadoActividad(
            Long id,
            String nuevoEstado,
            String motivo,
            String contacto) {

        Actividad a = actividadRepository.findById(id).orElse(null);

        if (a == null) {
            return "La actividad no existe.";
        }

        if (!"ACTIVA".equals(nuevoEstado)
                && !"BAJA".equals(nuevoEstado)) {
            return "Estado no válido.";
        }

        boolean yaEstaba = nuevoEstado.equals(a.getEstado());

        boolean quitarUnaPausada =
                yaEstaba
                        && "BAJA".equals(nuevoEstado)
                        && !a.quitadaPorAdministrador();

        if (yaEstaba && !quitarUnaPausada) {
            return "La actividad ya estaba en ese estado.";
        }

        if ("BAJA".equals(nuevoEstado)) {

            if (motivo == null || motivo.isBlank()) {
                return "Escribí el motivo: el prestador lo va a ver.";
            }

            if (contacto == null
                    || !contacto.trim().matches(
                    "[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {

                return "Dejá un correo válido para que el prestador te pueda contactar.";
            }

            a.setMotivoBajaAdmin(
                    motivo.trim().length() > 500
                            ? motivo.trim().substring(0, 500)
                            : motivo.trim()
            );

            a.setContactoBajaAdmin(
                    contacto.trim().toLowerCase()
            );

            a.setFechaBajaAdmin(LocalDateTime.now());
            a.setBajaPorAdministrador(true);

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

        return Normalizer.normalize(
                        texto,
                        Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
