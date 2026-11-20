package uy.edu.um.proytic2026.controllers;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import uy.edu.um.proytic2026.entities.Prestador;
import uy.edu.um.proytic2026.entities.Turista;
import uy.edu.um.proytic2026.entities.Usuario;
import uy.edu.um.proytic2026.repository.UsuarioRepository;
import uy.edu.um.proytic2026.repository.ActividadRepository;
import uy.edu.um.proytic2026.repository.EmprendimientoRepository;
import uy.edu.um.proytic2026.repository.ReservaRepository;
import uy.edu.um.proytic2026.entities.Actividad;
import uy.edu.um.proytic2026.entities.EstadoEmprendimiento;
import uy.edu.um.proytic2026.entities.EstadoReserva;
import uy.edu.um.proytic2026.entities.Horario;
import uy.edu.um.proytic2026.entities.Reserva;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

// Muestra las pantallas principales (las páginas HTML de la carpeta templates).
// Quién puede entrar a cada panel lo controla ControlDeAccesoInterceptor.
@Controller
public class PantallasWebController {

    private final UsuarioRepository usuarioRepository;

    // Para los números del resumen de cada panel
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private EmprendimientoRepository emprendimientoRepository;
    @Autowired private uy.edu.um.proytic2026.services.CatalogoService catalogoService;
    @Autowired private uy.edu.um.proytic2026.services.ImagenService imagenService;
    @Autowired private uy.edu.um.proytic2026.services.ResenaService resenaService;

    public PantallasWebController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/")
    public String inicio() {
        return "inicio";               // templates/inicio.html
    }

    @GetMapping("/panel/prestador")
    public String panelPrestador(HttpSession session, Model model) {
        // El id del usuario logueado lo guardó LoginController en la sesión
        Long usuarioId = (Long) session.getAttribute("usuarioId");
        if (usuarioId == null) {
            return "redirect:/login";
        }

        Usuario usuario = usuarioRepository.findById(usuarioId).orElse(null);
        if (!(usuario instanceof Prestador prestador)) {
            return "redirect:/login";
        }

        // Datos que va a mostrar la página
        model.addAttribute("nombre", prestador.getName());
        model.addAttribute("emprendimiento", prestador.getEmprendimiento());

        // Resumen: actividades publicadas, próximas salidas, reservas activas y lo cobrado
        long publicadas = 0, proximasSalidas = 0, reservasActivas = 0;
        BigDecimal cobrado = BigDecimal.ZERO;
        if (prestador.getEmprendimiento() != null) {
            LocalDate hoy = LocalDate.now();
            for (Actividad a : actividadRepository.findByEmprendimientoIdOrderByIdDesc(prestador.getEmprendimiento().getId())) {
                if ("ACTIVA".equals(a.getEstado())) publicadas++;
                for (Horario h : a.getHorarios()) {
                    // Solo las que siguen en pie (ni canceladas ni eliminadas) y todavía no empezaron
                    if ("ACTIVA".equals(a.getEstado()) && h.disponible() && h.inicio().isAfter(LocalDateTime.now())) proximasSalidas++;
                }
            }
            for (Reserva r : reservaRepository.reservasDelEmprendimiento(prestador.getEmprendimiento().getId())) {
                if (r.getEstado() != EstadoReserva.CANCELADA) reservasActivas++;
                if (r.getEstado() == EstadoReserva.CONFIRMADA) cobrado = cobrado.add(r.getMontoTotal());
            }
        }
        // Próximas salidas (las 6 más cercanas) con cuántos lugares están ocupados
        java.util.List<Horario> proximas = new java.util.ArrayList<>();
        if (prestador.getEmprendimiento() != null) {
            LocalDateTime ahoraP = LocalDateTime.now();
            for (Actividad a : actividadRepository.findByEmprendimientoIdOrderByIdDesc(prestador.getEmprendimiento().getId())) {
                if (!"ACTIVA".equals(a.getEstado())) continue;
                for (Horario h : a.getHorarios()) {
                    if (h.disponible() && h.inicio().isAfter(ahoraP)) proximas.add(h);
                }
            }
            proximas.sort(Comparator.comparing(Horario::inicio));
        }
        model.addAttribute("proximas", proximas.size() > 6 ? proximas.subList(0, 6) : proximas);
        model.addAttribute("publicadas", publicadas);
        model.addAttribute("proximasSalidas", proximasSalidas);
        model.addAttribute("reservasActivas", reservasActivas);
        model.addAttribute("cobrado", cobrado);
        return "panel-prestador";      // templates/panel-prestador.html
    }

    @GetMapping("/panel/turista")
    public String panelTurista(HttpSession session, Model model) {
        Long usuarioId = (Long) session.getAttribute("usuarioId");
        if (usuarioId == null) {
            return "redirect:/login";
        }

        Usuario usuario = usuarioRepository.findById(usuarioId).orElse(null);
        if (!(usuario instanceof Turista turista)) {
            return "redirect:/login";
        }

        model.addAttribute("nombre", turista.getName());
        model.addAttribute("turista", turista);      // para la tarjeta "Mis datos"

        // Resumen: próxima actividad y cuántas reservas tiene en cada estado
        List<Reserva> reservas = reservaRepository.findByTuristaIdOrderByFechaCreacionDesc(turista.getId());
        LocalDateTime ahora = LocalDateTime.now();
        Reserva proxima = reservas.stream()
                .filter(r -> r.getEstado() == EstadoReserva.CONFIRMADA || r.getEstado() == EstadoReserva.PENDIENTE_PAGO)
                .filter(r -> r.getHorario().getFecha().atTime(r.getHorario().getHoraInicio()).isAfter(ahora))
                .min(Comparator.comparing((Reserva r) -> r.getHorario().getFecha()).thenComparing(r -> r.getHorario().getHoraInicio()))
                .orElse(null);
        model.addAttribute("proxima", proxima);
        model.addAttribute("confirmadas", reservas.stream().filter(r -> r.getEstado() == EstadoReserva.CONFIRMADA).count());
        model.addAttribute("pendientes", reservas.stream().filter(r -> r.getEstado() == EstadoReserva.PENDIENTE_PAGO).count());
        model.addAttribute("enEspera", reservas.stream().filter(r -> r.getEstado() == EstadoReserva.EN_ESPERA).count());

        // "Actividades para vos": las más nuevas del portal que tienen fechas para reservar
        List<Actividad> sugeridas = catalogoService.buscar(null, null, null, null, null).stream()
                .filter(a -> !catalogoService.proximosHorarios(a).isEmpty())
                .limit(6).toList();
        model.addAttribute("sugeridas", sugeridas);
        model.addAttribute("portadas", imagenService.portadas());
        model.addAttribute("valoraciones", resenaService.promedios());
        return "panel-turista";        // templates/panel-turista.html
    }

    @GetMapping("/panel/admin")
    public String panelAdmin(Model model) {
        // Resumen del portal
        long pendientes = 0, aceptados = 0;
        for (var e : emprendimientoRepository.findAll()) {
            if (e.getEstado() == EstadoEmprendimiento.PENDIENTE) pendientes++;
            if (e.getEstado() == EstadoEmprendimiento.ACEPTADO) aceptados++;
        }
        long publicadas = actividadRepository.findAll().stream()
                .filter(a -> "ACTIVA".equals(a.getEstado()) && a.getEmprendimiento().getEstado() == EstadoEmprendimiento.ACEPTADO)
                .count();
        model.addAttribute("statPendientes", pendientes);
        model.addAttribute("statAceptados", aceptados);
        model.addAttribute("statActividades", publicadas);
        model.addAttribute("statReservas", reservaRepository.count());
        return "panel-admin";          // templates/panel-admin.html
    }
}
