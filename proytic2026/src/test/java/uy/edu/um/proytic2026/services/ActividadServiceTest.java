package uy.edu.um.proytic2026.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Tests unitarios de ActividadService (CU-P06 a CU-P09).
@ExtendWith(MockitoExtension.class)
class ActividadServiceTest {

    @Mock private ActividadRepository actividadRepository;
    @Mock private HorarioRepository horarioRepository;
    @Mock private PrestadorRepository prestadorRepository;
    @Mock private ReservaRepository reservaRepository;
    @Mock private TipoActividadRepository tipoActividadRepository;
    @Mock private TipoEquipamientoRepository tipoEquipamientoRepository;
    @Mock private ReservaService reservaService;
    @Mock private ImagenActividadRepository imagenActividadRepository;

    @InjectMocks private ActividadService actividadService;

    private static final Long PRESTADOR_ID = 7L;
    private Emprendimiento emprendimiento;

    @BeforeEach
    void prepararDatos() {
        emprendimiento = new Emprendimiento();
        emprendimiento.setId(3L);
        emprendimiento.setNombre("Kayak del Este");

    }

    // El prestador logueado, dueño del emprendimiento
    private void prestadorLogueado() {
        Empresa prestador = new Empresa();
        prestador.setId(PRESTADOR_ID);
        prestador.setEmprendimiento(emprendimiento);
        when(prestadorRepository.findById(PRESTADOR_ID)).thenReturn(Optional.of(prestador));
    }

    private Actividad crearConDatosValidos() {
        return actividadService.crearActividad(PRESTADOR_ID, "Kayak al atardecer", "Remada guiada.", "Rocha",
                "La Paloma", new BigDecimal("1500"), "Mayores de 12", 60, null, null);
    }

    private Actividad actividadPropia() {
        Actividad actividad = new Actividad();
        actividad.setId(10L);
        actividad.setEstado(ActividadService.ACTIVA);
        actividad.setEmprendimiento(emprendimiento);
        return actividad;
    }

    @Test
    void noSePuedeCrearActividadConEmprendimientoPendiente() {
        prestadorLogueado();
        emprendimiento.setEstado(EstadoEmprendimiento.PENDIENTE);

        assertNull(crearConDatosValidos());
        verify(actividadRepository, never()).save(any());
    }

    @Test
    void conEmprendimientoAceptadoSeCreaActiva() {
        prestadorLogueado();
        emprendimiento.setEstado(EstadoEmprendimiento.ACEPTADO);
        when(actividadRepository.save(any(Actividad.class))).thenAnswer(inv -> inv.getArgument(0));

        Actividad actividad = crearConDatosValidos();

        assertNotNull(actividad);
        assertEquals(ActividadService.ACTIVA, actividad.getEstado());
        assertSame(emprendimiento, actividad.getEmprendimiento());
        assertEquals("Rocha", actividad.getDepartamento());
    }

    @Test
    void validaLosDatosObligatorios() {
        assertEquals("Completá el título, la descripción y el departamento.",
                actividadService.validarDatos("", "x", "Rocha", BigDecimal.TEN, 0));
        assertEquals("Elegí un departamento de la lista.",
                actividadService.validarDatos("Título", "x", "Narnia", BigDecimal.TEN, 0));
        assertEquals("Ingresá un precio válido (0 o más).",
                actividadService.validarDatos("Título", "x", "Rocha", new BigDecimal("-1"), 0));
        assertNull(actividadService.validarDatos("Título", "x", "Rocha", BigDecimal.ZERO, 0));
    }

    @Test
    void noSePuedeEditarUnaActividadConReservas() {
        prestadorLogueado();
        Actividad actividad = actividadPropia();
        when(actividadRepository.findById(10L)).thenReturn(Optional.of(actividad));
        when(reservaRepository.actividadTieneReservasVigentes(eq(10L), eq(EstadoReserva.CANCELADA), any())).thenReturn(true);

        String error = actividadService.editarActividad(PRESTADOR_ID, 10L, "Nuevo", "x", "Rocha", null,
                BigDecimal.TEN, null, 0, null, null);

        assertEquals("La actividad tiene reservas vigentes: no se puede modificar hasta que pasen esas salidas. Sí podés agregarle horarios.", error);
        verify(actividadRepository, never()).save(any());
    }

    @Test
    void noSePuedeEditarUnaActividadAjena() {
        prestadorLogueado();
        Actividad ajena = actividadPropia();
        Emprendimiento otro = new Emprendimiento();
        otro.setId(99L);
        ajena.setEmprendimiento(otro);
        when(actividadRepository.findById(10L)).thenReturn(Optional.of(ajena));

        assertEquals("La actividad no existe.", actividadService.editarActividad(PRESTADOR_ID, 10L, "Nuevo", "x",
                "Rocha", null, BigDecimal.TEN, null, 0, null, null));
    }

    @Test
    void darDeBajaEsLogica() {
        prestadorLogueado();
        Actividad actividad = actividadPropia();
        when(actividadRepository.findById(10L)).thenReturn(Optional.of(actividad));

        assertTrue(actividadService.darDeBaja(PRESTADOR_ID, 10L));
        assertEquals(ActividadService.BAJA, actividad.getEstado());
        verify(actividadRepository).save(actividad);
        verify(actividadRepository, never()).delete(any());
    }

    @Test
    void alCrearUnHorarioLosCuposDisponiblesSonLosTotales() {
        prestadorLogueado();
        Actividad actividad = actividadPropia();
        when(actividadRepository.findById(10L)).thenReturn(Optional.of(actividad));
        when(horarioRepository.findById(any(HorarioId.class))).thenReturn(Optional.empty());
        when(horarioRepository.save(any(Horario.class))).thenAnswer(inv -> inv.getArgument(0));
        LocalDate fecha = LocalDate.now().plusDays(5);

        Horario horario = actividadService.agregarHorario(PRESTADOR_ID, 10L, fecha, LocalTime.of(9, 0),
                LocalTime.of(11, 0), 12, true, 120, 50);

        assertNotNull(horario);
        assertEquals(12, horario.getCuposTotales());
        assertEquals(12, horario.getCuposDisponibles());
        assertEquals(50, horario.getPorcentajeReembolso());
    }

    @Test
    void rechazaHorariosEnElPasadoOConHorasAlReves() {
        Actividad actividad = actividadPropia();

        assertEquals("El horario tiene que ser en el futuro.", actividadService.validarHorario(actividad,
                LocalDate.now().minusDays(1), LocalTime.of(9, 0), LocalTime.of(11, 0), 10, false, null, null));
        assertEquals("La hora de fin tiene que ser después de la de inicio.", actividadService.validarHorario(actividad,
                LocalDate.now().plusDays(1), LocalTime.of(11, 0), LocalTime.of(9, 0), 10, false, null, null));
    }

    @Test
    void noSePuedeEliminarUnHorarioConReservas() {
        prestadorLogueado();
        Actividad actividad = actividadPropia();
        LocalDate fecha = LocalDate.now().plusDays(2);
        LocalTime hora = LocalTime.of(9, 0);
        when(actividadRepository.findById(10L)).thenReturn(Optional.of(actividad));
        when(horarioRepository.findById(new HorarioId(10L, fecha, hora))).thenReturn(Optional.of(new Horario()));
        when(reservaRepository.horarioTieneReservasActivas(10L, fecha, hora, EstadoReserva.CANCELADA)).thenReturn(true);

        assertTrue(actividadService.eliminarHorario(PRESTADOR_ID, 10L, fecha, hora).startsWith("Ese horario tiene reservas vigentes"));
        verify(horarioRepository, never()).deleteById(any());
    }

    @Test
    void unHorarioConSoloReservasCanceladasSeOcultaEnVezDeBorrarse() {
        prestadorLogueado();
        Actividad actividad = actividadPropia();
        LocalDate fecha = LocalDate.now().plusDays(2);
        LocalTime hora = LocalTime.of(9, 0);
        Horario horario = new Horario();
        horario.setCuposTotales(40);
        horario.setCuposDisponibles(40);
        when(actividadRepository.findById(10L)).thenReturn(Optional.of(actividad));
        when(horarioRepository.findById(new HorarioId(10L, fecha, hora))).thenReturn(Optional.of(horario));
        when(reservaRepository.horarioTieneReservasActivas(10L, fecha, hora, EstadoReserva.CANCELADA)).thenReturn(false);
        when(reservaRepository.horarioTieneReservas(10L, fecha, hora)).thenReturn(true);   // una cancelada

        assertNull(actividadService.eliminarHorario(PRESTADOR_ID, 10L, fecha, hora));
        assertTrue(horario.estaEliminado());
        verify(horarioRepository, never()).deleteById(any());   // no se borra: la reserva cancelada lo sigue usando
    }

    @Test
    void reservasRecibidasSonLasDeSuEmprendimiento() {
        prestadorLogueado();
        List<Reserva> reservas = List.of(new Reserva());
        when(reservaRepository.reservasDelEmprendimiento(3L)).thenReturn(reservas);

        assertSame(reservas, actividadService.reservasRecibidas(PRESTADOR_ID));
    }

    // ---------- El prestador cancela una salida ----------

    private Horario horarioPropio(java.time.LocalDateTime inicio) {
        Horario h = new Horario();
        h.setActividad(actividadPropia());
        h.setFecha(inicio.toLocalDate());
        h.setHoraInicio(inicio.toLocalTime());
        h.setCuposTotales(6);
        h.setCuposDisponibles(2);
        return h;
    }

    private void actividadYHorario(Horario h) {
        prestadorLogueado();
        when(actividadRepository.findById(10L)).thenReturn(Optional.of(h.getActividad()));
        when(horarioRepository.findById(new HorarioId(10L, h.getFecha(), h.getHoraInicio()))).thenReturn(Optional.of(h));
    }

    @Test
    void cancelarSalidaConTiempoDevuelveTodoYLaOcultaDelPortal() {
        Horario h = horarioPropio(java.time.LocalDateTime.now().plusDays(3).withNano(0));
        actividadYHorario(h);
        when(reservaService.cancelarPorPrestador(h, "Tormenta")).thenReturn(0);

        assertNull(actividadService.cancelarHorario(PRESTADOR_ID, 10L, h.getFecha(), h.getHoraInicio(), " Tormenta "));
        assertTrue(h.estaCancelado());
        assertEquals(0, h.getCuposDisponibles());           // nadie más puede reservar
        verify(reservaService).cancelarPorPrestador(h, "Tormenta");
    }

    @Test
    void noSePuedeCancelarLaSalidaConMenosDe24Horas() {
        Horario h = horarioPropio(java.time.LocalDateTime.now().plusHours(10).withNano(0));
        actividadYHorario(h);

        String error = actividadService.cancelarHorario(PRESTADOR_ID, 10L, h.getFecha(), h.getHoraInicio(), "Tormenta");

        assertTrue(error.startsWith("Faltan menos de 24 h"));
        assertFalse(h.estaCancelado());
        verifyNoInteractions(reservaService);
    }

    @Test
    void cancelarSalidaPideMotivo() {
        Horario h = horarioPropio(java.time.LocalDateTime.now().plusDays(3).withNano(0));
        actividadYHorario(h);

        assertNotNull(actividadService.cancelarHorario(PRESTADOR_ID, 10L, h.getFecha(), h.getHoraInicio(), "  "));
        verifyNoInteractions(reservaService);
    }

    // ---------- Pausar y eliminar ----------

    @Test
    void noSePuedePausarSiTieneHorariosProximos() {
        prestadorLogueado();
        Actividad actividad = actividadPropia();
        Horario proximo = new Horario();
        proximo.setFecha(LocalDate.now().plusDays(3));
        proximo.setHoraInicio(LocalTime.of(10, 0));
        actividad.getHorarios().add(proximo);
        when(actividadRepository.findById(10L)).thenReturn(Optional.of(actividad));

        assertFalse(actividadService.darDeBaja(PRESTADOR_ID, 10L));
        assertEquals(ActividadService.ACTIVA, actividad.getEstado());
        assertNotNull(actividadService.motivoParaNoPausar(actividad));
    }

    @Test
    void soloSeEliminaUnaActividadPausada() {
        prestadorLogueado();
        when(actividadRepository.findById(10L)).thenReturn(Optional.of(actividadPropia()));   // ACTIVA

        assertEquals("Primero pausá la actividad; después la podés eliminar.", actividadService.eliminarActividad(PRESTADOR_ID, 10L));
        verify(actividadRepository, never()).delete(any());
    }

    @Test
    void unaActividadPausadaConReservasViejasQuedaEliminadaSinBorrarse() {
        prestadorLogueado();
        Actividad actividad = actividadPropia();
        actividad.setEstado(ActividadService.BAJA);
        when(actividadRepository.findById(10L)).thenReturn(Optional.of(actividad));
        when(reservaRepository.actividadTieneReservas(10L)).thenReturn(true);

        assertNull(actividadService.eliminarActividad(PRESTADOR_ID, 10L));
        assertEquals(ActividadService.ELIMINADA, actividad.getEstado());
        verify(actividadRepository, never()).delete(any());
    }

    @Test
    void unaActividadPausadaSinReservasSeBorra() {
        prestadorLogueado();
        Actividad actividad = actividadPropia();
        actividad.setEstado(ActividadService.BAJA);
        when(actividadRepository.findById(10L)).thenReturn(Optional.of(actividad));
        when(reservaRepository.actividadTieneReservas(10L)).thenReturn(false);
        when(imagenActividadRepository.idsDeActividad(10L)).thenReturn(List.of());

        assertNull(actividadService.eliminarActividad(PRESTADOR_ID, 10L));
        verify(actividadRepository).delete(actividad);
    }
}
