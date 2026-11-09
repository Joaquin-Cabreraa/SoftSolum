package uy.edu.um.proytic2026.controllers;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.proytic2026.entities.Actividad;
import uy.edu.um.proytic2026.entities.Emprendimiento;
import uy.edu.um.proytic2026.entities.Horario;
import uy.edu.um.proytic2026.services.ActividadService;
import uy.edu.um.proytic2026.services.TiposService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Espacio del prestador: sus actividades, horarios y reservas recibidas.
// Todo cuelga de /panel/prestador, así que ControlDeAccesoInterceptor
// solo deja entrar a usuarios con rol EMPRESA / PARTICULAR.
@Controller
@RequestMapping("/panel/prestador")
public class PrestadorActividadesWebController {

    @Autowired private ActividadService actividadService;
    @Autowired private TiposService tiposService;

    private Long prestadorId(HttpSession session) {
        return (Long) session.getAttribute("usuarioId");
    }

    // ---------- Listado de actividades ----------

    @GetMapping("/actividades")
    public String misActividades(HttpSession session, Model model) {
        Long prestadorId = prestadorId(session);
        Emprendimiento emprendimiento = actividadService.emprendimientoDe(prestadorId);
        List<Actividad> actividades = actividadService.actividadesDelPrestador(prestadorId);

        // Cuáles tienen reservas (esas no se pueden editar)
        Set<Long> conReservas = new HashSet<>();
        for (Actividad actividad : actividades) {
            if (actividadService.tieneReservas(actividad)) conReservas.add(actividad.getId());
        }

        model.addAttribute("emprendimiento", emprendimiento);
        model.addAttribute("puedePublicar", actividadService.puedePublicar(emprendimiento));
        model.addAttribute("actividades", actividades);
        model.addAttribute("conReservas", conReservas);
        return "prestador-actividades";
    }

    // ---------- Alta ----------

    @GetMapping("/actividades/nueva")
    public String formularioNueva(HttpSession session, Model model, RedirectAttributes redirect) {
        if (!actividadService.puedePublicar(actividadService.emprendimientoDe(prestadorId(session)))) {
            redirect.addFlashAttribute("error", "Tu emprendimiento tiene que estar aceptado para publicar actividades.");
            return "redirect:/panel/prestador/actividades";
        }
        Actividad vacia = new Actividad();
        vacia.setMinPreviosReservar(0);
        cargarFormulario(model, vacia, new ArrayList<>(), new ArrayList<>(), false);
        return "actividad-form";
    }

    @PostMapping("/actividades/nueva")
    public String crear(@RequestParam(required = false) String titulo,
                        @RequestParam(required = false) String descripcion,
                        @RequestParam(required = false) String departamento,
                        @RequestParam(required = false) String localidad,
                        @RequestParam(required = false) Integer duracionMinutos,
                        @RequestParam(required = false) BigDecimal precio,
                        @RequestParam(required = false) String requisitosEdad,
                        @RequestParam(required = false) Integer minPreviosReservar,
                        @RequestParam(name = "tipos", required = false) List<Long> tipos,
                        @RequestParam(name = "equipamiento", required = false) List<Long> equipamiento,
                        @RequestParam(required = false) String nuevosTipos,
                        @RequestParam(required = false) String nuevoEquipamiento,
                        HttpSession session, Model model, RedirectAttributes redirect) {

        Long prestadorId = prestadorId(session);
        if (!actividadService.puedePublicar(actividadService.emprendimientoDe(prestadorId))) {
            redirect.addFlashAttribute("error", "Tu emprendimiento tiene que estar aceptado para publicar actividades.");
            return "redirect:/panel/prestador/actividades";
        }

        String error = actividadService.validarDatos(titulo, descripcion, departamento, duracionMinutos, precio, minPreviosReservar);
        Actividad actividad = null;
        if (error == null) {
            // Tipos y equipamiento que escribió a mano (se crean si no existen)
            tipos = sumar(tipos, tiposService.obtenerOCrearTiposActividad(nuevosTipos));
            equipamiento = sumar(equipamiento, tiposService.obtenerOCrearTiposEquipamiento(nuevoEquipamiento));
            actividad = actividadService.crearActividad(prestadorId, titulo, descripcion, departamento, localidad,
                    duracionMinutos, precio, requisitosEdad, minPreviosReservar, tipos, equipamiento);
            if (actividad == null) error = "No se pudo crear la actividad.";
        }

        if (error != null) {
            // Volvemos al formulario con lo que había escrito
            Actividad escrita = armarActividad(titulo, descripcion, departamento, localidad, duracionMinutos,
                    precio, requisitosEdad, minPreviosReservar);
            cargarFormulario(model, escrita, tipos, equipamiento, false);
            model.addAttribute("error", error);
            return "actividad-form";
        }

        redirect.addFlashAttribute("exito", "Actividad creada. Ahora agregale fechas y horarios para que los turistas puedan reservar.");
        return "redirect:/panel/prestador/actividades/" + actividad.getId() + "/horarios";
    }

    // ---------- Edición ----------

    @GetMapping("/actividades/{id}/editar")
    public String formularioEditar(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes redirect) {
        Actividad actividad = actividadService.actividadDelPrestador(prestadorId(session), id);
        if (actividad == null) {
            redirect.addFlashAttribute("error", "La actividad no existe.");
            return "redirect:/panel/prestador/actividades";
        }
        if (actividadService.tieneReservas(actividad)) {
            redirect.addFlashAttribute("error", "Esa actividad ya tiene reservas: no se puede modificar.");
            return "redirect:/panel/prestador/actividades";
        }

        List<Long> tipos = new ArrayList<>();
        actividad.getTipoActividad().forEach(t -> tipos.add(t.getId()));
        List<Long> equipamiento = new ArrayList<>();
        actividad.getTiposEquipamiento().forEach(e -> equipamiento.add(e.getId()));

        cargarFormulario(model, actividad, tipos, equipamiento, true);
        return "actividad-form";
    }

    @PostMapping("/actividades/{id}/editar")
    public String editar(@PathVariable Long id,
                         @RequestParam(required = false) String titulo,
                         @RequestParam(required = false) String descripcion,
                         @RequestParam(required = false) String departamento,
                         @RequestParam(required = false) String localidad,
                         @RequestParam(required = false) Integer duracionMinutos,
                         @RequestParam(required = false) BigDecimal precio,
                         @RequestParam(required = false) String requisitosEdad,
                         @RequestParam(required = false) Integer minPreviosReservar,
                         @RequestParam(name = "tipos", required = false) List<Long> tipos,
                         @RequestParam(name = "equipamiento", required = false) List<Long> equipamiento,
                         @RequestParam(required = false) String nuevosTipos,
                         @RequestParam(required = false) String nuevoEquipamiento,
                         HttpSession session, Model model, RedirectAttributes redirect) {

        // Tipos y equipamiento que escribió a mano (se crean si no existen)
        tipos = sumar(tipos, tiposService.obtenerOCrearTiposActividad(nuevosTipos));
        equipamiento = sumar(equipamiento, tiposService.obtenerOCrearTiposEquipamiento(nuevoEquipamiento));

        String error = actividadService.editarActividad(prestadorId(session), id, titulo, descripcion, departamento,
                localidad, duracionMinutos, precio, requisitosEdad, minPreviosReservar, tipos, equipamiento);

        if (error != null) {
            Actividad escrita = armarActividad(titulo, descripcion, departamento, localidad, duracionMinutos,
                    precio, requisitosEdad, minPreviosReservar);
            escrita.setId(id);
            cargarFormulario(model, escrita, tipos, equipamiento, true);
            model.addAttribute("error", error);
            return "actividad-form";
        }

        redirect.addFlashAttribute("exito", "Cambios guardados.");
        return "redirect:/panel/prestador/actividades";
    }

    // ---------- Baja ----------

    @PostMapping("/actividades/{id}/baja")
    public String darDeBaja(@PathVariable Long id, HttpSession session, RedirectAttributes redirect) {
        if (actividadService.darDeBaja(prestadorId(session), id)) {
            redirect.addFlashAttribute("exito", "La actividad se dio de baja y ya no aparece en el portal. Las reservas hechas se conservan.");
        } else {
            redirect.addFlashAttribute("error", "No se pudo dar de baja la actividad.");
        }
        return "redirect:/panel/prestador/actividades";
    }

    // ---------- Horarios ----------

    @GetMapping("/actividades/{id}/horarios")
    public String horarios(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes redirect) {
        Actividad actividad = actividadService.actividadDelPrestador(prestadorId(session), id);
        if (actividad == null) {
            redirect.addFlashAttribute("error", "La actividad no existe.");
            return "redirect:/panel/prestador/actividades";
        }
        model.addAttribute("actividad", actividad);
        model.addAttribute("horarios", actividadService.horariosDe(actividad));
        model.addAttribute("hoy", LocalDate.now());
        return "actividad-horarios";
    }

    @PostMapping("/actividades/{id}/horarios")
    public String agregarHorario(@PathVariable Long id,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaInicio,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaFin,
                                 @RequestParam(required = false) Integer cupos,
                                 @RequestParam(defaultValue = "false") boolean cancelable,
                                 @RequestParam(required = false) Integer minMinutosCancelacion,
                                 @RequestParam(required = false) Integer porcentajeReembolso,
                                 HttpSession session, RedirectAttributes redirect) {

        Long prestadorId = prestadorId(session);
        Actividad actividad = actividadService.actividadDelPrestador(prestadorId, id);
        String error = actividadService.validarHorario(actividad, fecha, horaInicio, horaFin, cupos,
                cancelable, minMinutosCancelacion, porcentajeReembolso);

        if (error == null) {
            Horario horario = actividadService.agregarHorario(prestadorId, id, fecha, horaInicio, horaFin, cupos,
                    cancelable, minMinutosCancelacion, porcentajeReembolso);
            if (horario == null) error = "No se pudo guardar el horario.";
        }

        if (error != null) {
            redirect.addFlashAttribute("error", error);
        } else {
            redirect.addFlashAttribute("exito", "Horario agregado: ya se puede reservar desde el portal.");
        }
        return "redirect:/panel/prestador/actividades/" + id + "/horarios";
    }

    @PostMapping("/actividades/{id}/horarios/eliminar")
    public String eliminarHorario(@PathVariable Long id,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaInicio,
                                  HttpSession session, RedirectAttributes redirect) {
        String error = actividadService.eliminarHorario(prestadorId(session), id, fecha, horaInicio);
        if (error != null) {
            redirect.addFlashAttribute("error", error);
        } else {
            redirect.addFlashAttribute("exito", "Horario eliminado.");
        }
        return "redirect:/panel/prestador/actividades/" + id + "/horarios";
    }

    // ---------- Reservas recibidas (CU-P11) ----------

    @GetMapping("/reservas")
    public String reservasRecibidas(HttpSession session, Model model) {
        model.addAttribute("reservas", actividadService.reservasRecibidas(prestadorId(session)));
        return "prestador-reservas";
    }

    // ---------- Ayuda ----------

    // Datos que necesita el formulario de actividad (alta y edición)
    private void cargarFormulario(Model model, Actividad actividad, List<Long> tiposElegidos,
                                  List<Long> equipamientoElegido, boolean edicion) {
        model.addAttribute("actividad", actividad);
        model.addAttribute("edicion", edicion);
        model.addAttribute("departamentos", ActividadService.DEPARTAMENTOS);
        model.addAttribute("tipos", tiposService.tiposDeActividad());
        model.addAttribute("equipamientos", tiposService.tiposDeEquipamiento());
        model.addAttribute("tiposElegidos", tiposElegidos == null ? new ArrayList<>() : tiposElegidos);
        model.addAttribute("equipamientoElegido", equipamientoElegido == null ? new ArrayList<>() : equipamientoElegido);
    }

    // Junta los ids marcados en las casillas con los de los tipos nuevos (sin repetir)
    private List<Long> sumar(List<Long> marcados, List<Long> nuevos) {
        List<Long> todos = new ArrayList<>();
        if (marcados != null) todos.addAll(marcados);
        for (Long id : nuevos) {
            if (!todos.contains(id)) todos.add(id);
        }
        return todos;
    }

    // Una actividad "de mentira" (no se guarda) para volver a llenar el formulario
    private Actividad armarActividad(String titulo, String descripcion, String departamento, String localidad,
                                     Integer duracionMinutos, BigDecimal precio, String requisitosEdad,
                                     Integer minPreviosReservar) {
        Actividad actividad = new Actividad();
        actividad.setTitulo(titulo);
        actividad.setDescripcion(descripcion);
        actividad.setDepartamento(departamento);
        actividad.setLocalidad(localidad);
        actividad.setDuracionMinutos(duracionMinutos);
        actividad.setPrecio(precio);
        actividad.setRequisitosEdad(requisitosEdad);
        actividad.setMinPreviosReservar(minPreviosReservar);
        return actividad;
    }
}
