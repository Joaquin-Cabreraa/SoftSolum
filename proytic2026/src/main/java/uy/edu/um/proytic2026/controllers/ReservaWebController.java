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
        if (horario.getCuposDisponibles() < 1 || !catalogoService.sePuedeReservar(horario, LocalDateTime.now())) {
            redirect.addFlashAttribute("error", "Ese horario ya no admite reservas.");
            return "redirect:/actividades/" + actividadId;
        }

        model.addAttribute("horario", horario);
        model.addAttribute("actividad", horario.getActividad());
        model.addAttribute("titular", datosDelTitular(session));
        return "reservar";               // templates/reservar.html
    }

    // Recibe el formulario y crea la reserva (CU-T06)
    @PostMapping("/panel/turista/reservar")
    public String reservar(@RequestParam Long actividadId,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime hora,
                           @RequestParam int cantidad,
                           @RequestParam(required = false) String observacion,
                           HttpServletRequest request, HttpSession session,
                           Model model, RedirectAttributes redirect) {

        Long turistaId = (Long) session.getAttribute("usuarioId");

        // Los campos de cada persona se llaman igual (nombre, nombre, nombre...).
        // getParameterValues los devuelve todos, en el orden del formulario.
        List<String> nombres = valores(request, "nombre");
        List<String> apellidos = valores(request, "apellido");
        List<String> cis = valores(request, "ci");
        List<String> fechasNacimiento = valores(request, "fechaNacimiento");

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
            Horario horario = horarioRepository.findById(new HorarioId(actividadId, fecha, hora)).orElse(null);
            if (horario == null) {
                redirect.addFlashAttribute("error", error);
                return "redirect:/actividades";
            }
            model.addAttribute("horario", horario);
            model.addAttribute("actividad", horario.getActividad());
            model.addAttribute("titular", datosDelTitular(session));
            model.addAttribute("error", error);
            // Devolvemos lo que había escrito, para no tener que escribirlo de nuevo
            model.addAttribute("cantidad", cantidad);
            model.addAttribute("nombres", nombres);
            model.addAttribute("apellidos", apellidos);
            model.addAttribute("cis", cis);
            model.addAttribute("fechasNacimiento", fechasNacimiento);
            model.addAttribute("observacion", observacion);
            return "reservar";
        }

        // 3. Listo: a la pantalla de pago de la reserva recién creada (CU-T07)
        redirect.addFlashAttribute("exito", "¡Reserva creada! Tus cupos quedan guardados por " + ReservaService.HORAS_PARA_PAGAR
                + " horas: completá el pago para confirmarla.");
        return "redirect:/panel/turista/reservas/" + reserva.getId() + "/pagar";
    }

    // Mis reservas (CU-T09)
    @GetMapping("/panel/turista/reservas")
    public String misReservas(HttpSession session, Model model) {
        Long turistaId = (Long) session.getAttribute("usuarioId");
        List<Reserva> reservas = reservaService.reservasDelTurista(turistaId);

        model.addAttribute("reservas", reservas);
        model.addAttribute("reservaService", reservaService);   // para calcular el vencimiento en la página
        model.addAttribute("pagoService", pagoService);         // para el botón "Pagar" / "Reintentar pago"
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
