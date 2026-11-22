package uy.edu.um.proytic2026.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.proytic2026.entities.Horario;
import uy.edu.um.proytic2026.entities.HorarioId;
import uy.edu.um.proytic2026.entities.Pago;
import uy.edu.um.proytic2026.entities.Reserva;
import uy.edu.um.proytic2026.entities.Turista;
import uy.edu.um.proytic2026.repository.HorarioRepository;
import uy.edu.um.proytic2026.repository.TuristaRepository;
import uy.edu.um.proytic2026.services.CatalogoService;
import uy.edu.um.proytic2026.services.PagoService;
import uy.edu.um.proytic2026.services.ReservaService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

// Reservar y "Mis reservas". Todo cuelga de /panel/turista, así que
// ControlDeAccesoInterceptor solo deja pasar a usuarios con rol TURISTA.
@Controller
public class ReservaWebController {

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private uy.edu.um.proytic2026.services.ResenaService resenaService;

    @Autowired
    private PagoService pagoService;

    @Autowired
    private CatalogoService catalogoService;

    @Autowired
    private HorarioRepository horarioRepository;

    @Autowired
    private TuristaRepository turistaRepository;

    // Formulario de reserva para un horario concreto (paso 1 de CU-T06)
    // Ej: /panel/turista/reservar?actividadId=3&fecha=2026-12-01&hora=09:00
    @GetMapping("/panel/turista/reservar")
    public String formularioReserva(@RequestParam Long actividadId,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime hora,
                                    HttpSession session, Model model, RedirectAttributes redirect) {

        Horario horario = horarioRepository.findById(new HorarioId(actividadId, fecha, hora)).orElse(null);

        if (horario == null || !catalogoService.estaPublicada(horario.getActividad())) {
            redirect.addFlashAttribute("error", "El horario elegido no existe.");
            return "redirect:/actividades";
        }
        if (!catalogoService.sePuedeReservar(horario, LocalDateTime.now())) {
            redirect.addFlashAttribute("error", "Ese horario ya no admite reservas.");
            return "redirect:/actividades/" + actividadId;
        }

        cargarFormulario(model, horario, session);
        return "reservar";               // templates/reservar.html
    }

    // Datos que usa la página reservar.html. Si el horario está lleno, la página
    // se muestra en modo "lista de espera".
    private void cargarFormulario(Model model, Horario horario, HttpSession session) {
        model.addAttribute("horario", horario);
        model.addAttribute("actividad", horario.getActividad());
        model.addAttribute("titular", datosDelTitular(session));
        model.addAttribute("listaDeEspera", horario.getCuposDisponibles() < 1);
        model.addAttribute("precioPorPersona", horario.precioActual());
        model.addAttribute("conDescuento", horario.enDescuentoUltimaHora());
    }

    // Recibe el formulario y crea la reserva (CU-T06)
    @PostMapping("/panel/turista/reservar")
    public String reservar(@RequestParam Long actividadId,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime hora,
                           @RequestParam int cantidad,
                           @RequestParam(required = false) String observacion,
                           @RequestParam(defaultValue = "false") boolean listaDeEspera,
                           HttpServletRequest request, HttpSession session,
                           Model model, RedirectAttributes redirect) {

        Long turistaId = (Long) session.getAttribute("usuarioId");

        // Los campos de cada persona se llaman igual (nombre, nombre, nombre...).
        // getParameterValues los devuelve todos, en el orden del formulario.
        List<String> nombres = valores(request, "nombre");
        List<String> apellidos = valores(request, "apellido");
        List<String> cis = valores(request, "ci");
        List<String> fechasNacimiento = valores(request, "fechaNacimiento");

        // Lista de espera: el horario está lleno, se anota sin pagar ni retener cupos
        if (listaDeEspera) {
            String error = reservaService.validarListaDeEspera(turistaId, actividadId, fecha, hora, cantidad,
                    nombres, apellidos, cis, fechasNacimiento);
            if (error == null) {
                Reserva espera = reservaService.anotarEnListaDeEspera(turistaId, actividadId, fecha, hora, cantidad,
                        nombres, apellidos, cis, fechasNacimiento, observacion);
                if (espera != null) {
                    redirect.addFlashAttribute("exito", "¡Listo! Estás en la lista de espera (lugar "
                            + reservaService.posicionEnListaDeEspera(espera) + "). Si se libera lugar te lo asignamos "
                            + "solo y vas a tener " + ReservaService.HORAS_PARA_PAGAR + " horas para pagarlo.");
                    return "redirect:/panel/turista/reservas";
                }
                error = "No se pudo anotar en la lista de espera.";
            }
            return volverAlFormulario(error, actividadId, fecha, hora, cantidad, nombres, apellidos, cis,
                    fechasNacimiento, observacion, session, model, redirect);
        }

        // 1. Validar: si hay error, volvemos a mostrar el formulario con el mensaje
        String error = reservaService.validarReserva(turistaId, actividadId, fecha, hora, cantidad,
                nombres, apellidos, cis, fechasNacimiento);

        Reserva reserva = null;
        if (error == null) {
            // 2. Crear (descuenta los cupos de forma atómica)
            reserva = reservaService.crearReserva(turistaId, actividadId, fecha, hora, cantidad,
                    nombres, apellidos, cis, fechasNacimiento, observacion);
            if (reserva == null) {
                error = "Otro turista acaba de reservar esos cupos y ya no alcanzan. Probá con menos personas u otro horario.";
            }
        }

        if (error != null) {
            return volverAlFormulario(error, actividadId, fecha, hora, cantidad, nombres, apellidos, cis,
                    fechasNacimiento, observacion, session, model, redirect);
        }

        // 3. Listo: directo a la pantalla de pago (CU-T07)
        redirect.addFlashAttribute("exito", "¡Reserva creada! Tus cupos quedan guardados "
                + ReservaService.HORAS_PARA_PAGAR + " horas mientras pagás.");
        return "redirect:/panel/turista/reservas/" + reserva.getId() + "/pagar";
    }

    // Vuelve a mostrar el formulario con el error y lo que había escrito (para no tener que escribirlo de nuevo)
    private String volverAlFormulario(String error, Long actividadId, LocalDate fecha, LocalTime hora, int cantidad,
                                      List<String> nombres, List<String> apellidos, List<String> cis,
                                      List<String> fechasNacimiento, String observacion,
                                      HttpSession session, Model model, RedirectAttributes redirect) {
        Horario horario = horarioRepository.findById(new HorarioId(actividadId, fecha, hora)).orElse(null);
        if (horario == null) {
            redirect.addFlashAttribute("error", error);
            return "redirect:/actividades";
        }
        cargarFormulario(model, horario, session);
        model.addAttribute("error", error);
        model.addAttribute("cantidad", cantidad);
        model.addAttribute("nombres", nombres);
        model.addAttribute("apellidos", apellidos);
        model.addAttribute("cis", cis);
        model.addAttribute("fechasNacimiento", fechasNacimiento);
        model.addAttribute("observacion", observacion);
        return "reservar";
    }

    // Cancelar una reserva propia (CU-T08)
    @PostMapping("/panel/turista/reservas/{id}/cancelar")
    public String cancelar(@org.springframework.web.bind.annotation.PathVariable Long id,
                           HttpSession session, RedirectAttributes redirect) {
        Long turistaId = (Long) session.getAttribute("usuarioId");
        // Lo que se le va a devolver (se calcula antes de cancelar, para el mensaje)
        Reserva reserva = reservaService.reservasDelTurista(turistaId).stream()
                .filter(r -> r.getId().equals(id)).findFirst().orElse(null);
        boolean estabaPaga = reserva != null && reserva.getEstado() == uy.edu.um.proytic2026.entities.EstadoReserva.CONFIRMADA;
        java.math.BigDecimal devuelto = estabaPaga ? reservaService.montoAReembolsar(reserva) : java.math.BigDecimal.ZERO;

        String error = reservaService.cancelarReserva(turistaId, id);
        if (error != null) {
            redirect.addFlashAttribute("error", error);
        } else if (estabaPaga && devuelto.signum() > 0) {
            redirect.addFlashAttribute("exito", "Reserva cancelada. Te devolvemos $U "
                    + devuelto.setScale(0, java.math.RoundingMode.HALF_UP) + " a la misma tarjeta.");
        } else if (estabaPaga) {
            redirect.addFlashAttribute("exito", "Reserva cancelada. Según la política de este horario, no hay reembolso.");
        } else {
            redirect.addFlashAttribute("exito", "Reserva cancelada. Los lugares quedaron libres.");
        }
        return "redirect:/panel/turista/reservas";
    }

    // Mis reservas (CU-T09)
    @GetMapping("/panel/turista/reservas")
    public String misReservas(HttpSession session, Model model) {
        Long turistaId = (Long) session.getAttribute("usuarioId");
        List<Reserva> reservas = reservaService.reservasDelTurista(turistaId);

        // Lugar en la fila de las que están en lista de espera
        Map<Long, Integer> posiciones = new HashMap<>();
        // Último intento de pago de las reservas pendientes (para "Reintentar pago")
        Map<Long, Pago> ultimosPagos = new HashMap<>();
        for (Reserva r : reservas) {
            if (r.getEstado() == uy.edu.um.proytic2026.entities.EstadoReserva.PENDIENTE_PAGO) {
                Pago ultimo = pagoService.ultimoIntento(r.getId());
                if (ultimo != null) {
                    ultimosPagos.put(r.getId(), ultimo);
                }
            }
            reservaService.codigoDe(r);   // código del QR (las reservas viejas no tenían)
            if (r.getEstado() == uy.edu.um.proytic2026.entities.EstadoReserva.EN_ESPERA) {
                posiciones.put(r.getId(), reservaService.posicionEnListaDeEspera(r));
            }
        }

        model.addAttribute("reservas", reservas);
        model.addAttribute("posiciones", posiciones);
        model.addAttribute("ultimosPagos", ultimosPagos);
        model.addAttribute("misResenas", resenaService.delTurista(reservas));   // reseña de cada reserva (si tiene)
        model.addAttribute("ahora", LocalDateTime.now());
        model.addAttribute("reservaService", reservaService);   // para calcular el vencimiento en la página
        return "mis-reservas";           // templates/mis-reservas.html
    }

    // Datos del turista logueado, para completar sola la "Persona 1" del formulario
    private Map<String, String> datosDelTitular(HttpSession session) {
        Map<String, String> datos = new HashMap<>();
        Long turistaId = (Long) session.getAttribute("usuarioId");
        Turista turista = turistaId == null ? null : turistaRepository.findById(turistaId).orElse(null);
        if (turista != null) {
            datos.put("nombre", turista.getName() == null ? "" : turista.getName());
            datos.put("apellido", turista.getLastName() == null ? "" : turista.getLastName());
            datos.put("ci", turista.getDocumento() == null ? "" : turista.getDocumento());
            datos.put("fechaNacimiento", turista.getFechaNacimiento() == null ? "" : turista.getFechaNacimiento().toString());
        }
        return datos;
    }

    // Todos los valores de un campo repetido, o una lista vacía si no vino
    private List<String> valores(HttpServletRequest request, String campo) {
        String[] valores = request.getParameterValues(campo);
        return valores == null ? new ArrayList<>() : Arrays.asList(valores);
    }
}
