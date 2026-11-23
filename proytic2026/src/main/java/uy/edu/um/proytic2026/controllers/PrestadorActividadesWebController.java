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
    @Autowired private uy.edu.um.proytic2026.services.ImagenService imagenService;

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
        model.addAttribute("portadas", imagenService.portadas());
        // Cuántos horarios próximos tiene cada una (con horarios próximos no se puede pausar)
        java.util.Map<Long, Long> horariosActivos = new java.util.HashMap<>();
        for (Actividad a : actividades) horariosActivos.put(a.getId(), actividadService.horariosActivos(a));
        model.addAttribute("horariosActivos", horariosActivos);
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
                        @RequestParam(required = false) BigDecimal precio,
                        @RequestParam(required = false) String requisitosEdad,
                        @RequestParam(required = false) Integer minPreviosReservar,
                        @RequestParam(name = "tipos", required = false) List<Long> tipos,
                        @RequestParam(name = "equipamiento", required = false) List<Long> equipamiento,
                        @RequestParam(required = false) String nuevosTipos,
                        @RequestParam(required = false) String nuevoEquipamiento,
                        @RequestParam(name = "incluido", required = false) List<Long> incluido,
                        @RequestParam(required = false) String nuevoIncluido,
                        @RequestParam(name = "imagenes", required = false) List<org.springframework.web.multipart.MultipartFile> imagenes,
                        HttpSession session, Model model, RedirectAttributes redirect) throws java.io.IOException {

        Long prestadorId = prestadorId(session);
        if (!actividadService.puedePublicar(actividadService.emprendimientoDe(prestadorId))) {
            redirect.addFlashAttribute("error", "Tu emprendimiento tiene que estar aceptado para publicar actividades.");
            return "redirect:/panel/prestador/actividades";
        }

        String error = actividadService.validarDatos(titulo, descripcion, departamento, precio, minPreviosReservar);
        if (error == null) {
            error = imagenService.validar(imagenes, 0);
        }
        Actividad actividad = null;
        if (error == null) {
            // Tipos y equipamiento que escribió a mano (se crean si no existen)
            tipos = sumar(tipos, tiposService.obtenerOCrearTiposActividad(nuevosTipos));
            equipamiento = sumar(equipamiento, tiposService.obtenerOCrearTiposEquipamiento(nuevoEquipamiento));
            incluido = sumar(incluido, tiposService.obtenerOCrearTiposEquipamiento(nuevoIncluido));
            actividad = actividadService.crearActividad(prestadorId, titulo, descripcion, departamento, localidad,
                    precio, requisitosEdad, minPreviosReservar, tipos, equipamiento);
            if (actividad == null) error = "No se pudo crear la actividad.";
            else {
                imagenService.actualizarFotos(actividad, imagenes, null);
                actividadService.actualizarEquipamientoIncluido(prestadorId, actividad.getId(), incluido);
            }
        }

        if (error != null) {
            // Volvemos al formulario con lo que había escrito
            Actividad escrita = armarActividad(titulo, descripcion, departamento, localidad,
                    precio, requisitosEdad, minPreviosReservar);
            model.addAttribute("incluidoElegido", incluido == null ? new ArrayList<>() : incluido);
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

        List<Long> incluido = new ArrayList<>();
        actividad.getEquipamientoIncluido().forEach(e -> incluido.add(e.getId()));
        model.addAttribute("incluidoElegido", incluido);
        cargarFormulario(model, actividad, tipos, equipamiento, true);
        return "actividad-form";
    }

    @PostMapping("/actividades/{id}/editar")
    public String editar(@PathVariable Long id,
                         @RequestParam(required = false) String titulo,
                         @RequestParam(required = false) String descripcion,
                         @RequestParam(required = false) String departamento,
                         @RequestParam(required = false) String localidad,
                         @RequestParam(required = false) BigDecimal precio,
                         @RequestParam(required = false) String requisitosEdad,
                         @RequestParam(required = false) Integer minPreviosReservar,
                         @RequestParam(name = "tipos", required = false) List<Long> tipos,
                         @RequestParam(name = "equipamiento", required = false) List<Long> equipamiento,
                         @RequestParam(required = false) String nuevosTipos,
                         @RequestParam(required = false) String nuevoEquipamiento,
                         @RequestParam(name = "incluido", required = false) List<Long> incluido,
                         @RequestParam(required = false) String nuevoIncluido,
                         @RequestParam(name = "imagenes", required = false) List<org.springframework.web.multipart.MultipartFile> imagenes,
                         @RequestParam(name = "eliminarImagenes", required = false) List<Long> eliminarImagenes,
                         HttpSession session, Model model, RedirectAttributes redirect) throws java.io.IOException {

        // Comprobar que la actividad pertenece al prestador autenticado
        Long idPrestador = prestadorId(session);

        Actividad actividad = actividadService.actividadDelPrestador(idPrestador, id);

        if (actividad == null) {
            redirect.addFlashAttribute("error",
                    "No tenés permiso para editar esta actividad.");
            return "redirect:/panel/prestador/actividades";
        }

        // Tipos y equipamiento que escribió a mano
        tipos = sumar(tipos, tiposService.obtenerOCrearTiposActividad(nuevosTipos));

        // Tipos y equipamiento que escribió a mano (se crean si no existen)
        tipos = sumar(tipos, tiposService.obtenerOCrearTiposActividad(nuevosTipos));
        equipamiento = sumar(equipamiento, tiposService.obtenerOCrearTiposEquipamiento(nuevoEquipamiento));
        incluido = sumar(incluido, tiposService.obtenerOCrearTiposEquipamiento(nuevoIncluido));

        // Fotos: las que quedan = las que tenía - las marcadas para borrar + las nuevas
        int quedan = imagenService.cantidad(id) - (eliminarImagenes == null ? 0 : eliminarImagenes.size());
        String error = imagenService.validar(imagenes, Math.max(quedan, 0));
        if (error == null) {
            error = actividadService.editarActividad(prestadorId(session), id, titulo, descripcion, departamento,
                    localidad, precio, requisitosEdad, minPreviosReservar, tipos, equipamiento);
        }
        if (error == null) {
            imagenService.actualizarFotos(actividadService.actividadDelPrestador(prestadorId(session), id),
                    imagenes, eliminarImagenes);
            actividadService.actualizarEquipamientoIncluido(prestadorId(session), id, incluido);
        }

        if (error != null) {
            Actividad escrita = armarActividad(titulo, descripcion, departamento, localidad,
                    precio, requisitosEdad, minPreviosReservar);
            escrita.setId(id);
            model.addAttribute("incluidoElegido", incluido == null ? new ArrayList<>() : incluido);
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
        Actividad actividad = actividadService.actividadDelPrestador(prestadorId(session), id);
        String motivo = actividad == null ? null : actividadService.motivoParaNoPausar(actividad);
        if (motivo != null) {
            redirect.addFlashAttribute("error", "No se puede pausar: " + motivo);
        } else if (actividadService.darDeBaja(prestadorId(session), id)) {
            redirect.addFlashAttribute("exito", "Publicación pausada: la actividad ya no aparece en el portal. "
                    + "Las reservas hechas se mantienen y la podés volver a publicar cuando quieras.");
        } else {
            redirect.addFlashAttribute("error", "No se pudo pausar la actividad.");
        }
        return "redirect:/panel/prestador/actividades";
    }

    @PostMapping("/actividades/{id}/eliminar")
    public String eliminarActividad(@PathVariable Long id, HttpSession session, RedirectAttributes redirect) {
        String error = actividadService.eliminarActividad(prestadorId(session), id);
        if (error != null) {
            redirect.addFlashAttribute("error", error);
        } else {
            redirect.addFlashAttribute("exito", "Actividad eliminada.");
        }
        return "redirect:/panel/prestador/actividades";
    }

    @PostMapping("/actividades/{id}/reactivar")
    public String reactivar(@PathVariable Long id, HttpSession session, RedirectAttributes redirect) {
        String error = actividadService.reactivar(prestadorId(session), id);
        if (error != null) {
            redirect.addFlashAttribute("error", error);
        } else {
            redirect.addFlashAttribute("exito", "La actividad volvió a publicarse en el portal.");
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
        model.addAttribute("ahora", java.time.LocalDateTime.now());
        model.addAttribute("horasCancelarSalida", uy.edu.um.proytic2026.entities.Horario.HORAS_ANTES_CANCELAR_SALIDA);
        model.addAttribute("hoy", LocalDate.now());
        return "actividad-horarios";
    }

    @PostMapping("/actividades/{id}/horarios")
    public String agregarHorario(@PathVariable Long id,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaInicio,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaFin,
                                 @RequestParam(required = false) Integer cupos,
                                 @RequestParam(defaultValue = "NO") String politica,
                                 @RequestParam(required = false) Integer horasCancelacion,
                                 @RequestParam(required = false) Integer porcentajeReembolso,
                                 HttpSession session, RedirectAttributes redirect) {

        // Política de cancelación elegida por el prestador:
        //   NO            -> no admite cancelación
        //   SIN_REEMBOLSO -> se puede cancelar hasta X horas antes, pero no se devuelve nada
        //   REEMBOLSO     -> se puede cancelar hasta X horas antes y se devuelve el % indicado
        boolean cancelable = !"NO".equals(politica);
        Integer minMinutosCancelacion = cancelable && horasCancelacion != null ? horasCancelacion * 60 : null;
        if ("SIN_REEMBOLSO".equals(politica)) {
            porcentajeReembolso = 0;
        } else if ("REEMBOLSO".equals(politica) && (porcentajeReembolso == null || porcentajeReembolso < 1)) {
            redirect.addFlashAttribute("error", "Indicá qué porcentaje se devuelve (entre 1 y 100).");
            return "redirect:/panel/prestador/actividades/" + id + "/horarios";
        }

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

    // Cancelar una salida que ya tiene reservas: reembolso del 100 % a todos (hasta 24 h antes)
    @PostMapping("/actividades/{id}/horarios/cancelar")
    public String cancelarHorario(@PathVariable Long id,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaInicio,
                                  @RequestParam(required = false) String motivo,
                                  HttpSession session, RedirectAttributes redirect) {
        String error = actividadService.cancelarHorario(prestadorId(session), id, fecha, horaInicio, motivo);
        if (error != null) {
            redirect.addFlashAttribute("error", error);
        } else {
            redirect.addFlashAttribute("exito", "Salida cancelada. A todos los turistas que habían pagado se les devolvió el 100 %, "
                    + "y en \"Mis reservas\" ven el motivo.");
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

    // ---------- Descuento de última hora ----------

    @PostMapping("/actividades/{id}/ultima-hora")
    public String ultimaHora(@PathVariable Long id,
                             @RequestParam(required = false) Integer descuento,
                             @RequestParam(required = false) Integer horas,
                             @RequestParam(defaultValue = "false") boolean quitar,
                             HttpSession session, RedirectAttributes redirect) {
        if (quitar) {
            descuento = null;
            horas = null;
        }
        String error = actividadService.configurarUltimaHora(prestadorId(session), id, descuento, horas);
        if (error != null) {
            redirect.addFlashAttribute("error", error);
        } else if (descuento == null) {
            redirect.addFlashAttribute("exito", "Descuento de última hora desactivado.");
        } else {
            redirect.addFlashAttribute("exito", "Listo: los lugares libres van a tener " + descuento
                    + " % de descuento en las " + horas + " horas antes de cada salida.");
        }
        return "redirect:/panel/prestador/actividades/" + id + "/horarios";
    }

    // Misma acción, pero llamada desde JavaScript (fetch) para no recargar la página:
    // así no se borra lo que el prestador estaba escribiendo en "Agregar horario".
    @PostMapping(value = "/actividades/{id}/ultima-hora", params = "ajax")
    @ResponseBody
    public java.util.Map<String, Object> ultimaHoraAjax(@PathVariable Long id,
                                                        @RequestParam(required = false) Integer descuento,
                                                        @RequestParam(required = false) Integer horas,
                                                        @RequestParam(defaultValue = "false") boolean quitar,
                                                        HttpSession session) {
        if (quitar) {
            descuento = null;
            horas = null;
        }
        String error = actividadService.configurarUltimaHora(prestadorId(session), id, descuento, horas);
        java.util.Map<String, Object> json = new java.util.LinkedHashMap<>();
        json.put("ok", error == null);
        json.put("activo", error == null && descuento != null);
        json.put("descuento", descuento);
        json.put("horas", horas);
        json.put("mensaje", error != null ? error
                : descuento == null ? "Descuento de última hora desactivado."
                : "Listo: " + descuento + " % de descuento en las " + horas + " horas antes de cada salida.");
        return json;
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
        model.addAttribute("imagenes", imagenService.idsDe(actividad.getId()));
        model.addAttribute("maxImagenes", uy.edu.um.proytic2026.services.ImagenService.MAXIMO_POR_ACTIVIDAD);
        model.addAttribute("departamentos", ActividadService.DEPARTAMENTOS);
        model.addAttribute("tipos", tiposService.tiposDeActividad());
        model.addAttribute("equipamientos", tiposService.tiposDeEquipamiento());
        model.addAttribute("tiposElegidos", tiposElegidos == null ? new ArrayList<>() : tiposElegidos);
        model.addAttribute("equipamientoElegido", equipamientoElegido == null ? new ArrayList<>() : equipamientoElegido);
        if (!model.containsAttribute("incluidoElegido")) {
            model.addAttribute("incluidoElegido", new ArrayList<>());
        }
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
                                     BigDecimal precio, String requisitosEdad,
                                     Integer minPreviosReservar) {
        Actividad actividad = new Actividad();
        actividad.setTitulo(titulo);
        actividad.setDescripcion(descripcion);
        actividad.setDepartamento(departamento);
        actividad.setLocalidad(localidad);
        actividad.setPrecio(precio);
        actividad.setRequisitosEdad(requisitosEdad);
        actividad.setMinPreviosReservar(minPreviosReservar);
        return actividad;
    }
}
