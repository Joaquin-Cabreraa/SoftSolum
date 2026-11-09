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
                "La Paloma", 120, new BigDecimal("1500"), "Mayores de 12", 60, null, null);
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
                actividadService.validarDatos("", "x", "Rocha", 60, BigDecimal.TEN, 0));
        assertEquals("Elegí un departamento de la lista.",
                actividadService.validarDatos("Título", "x", "Narnia", 60, BigDecimal.TEN, 0));
        assertEquals("Ingresá un precio válido (0 o más).",
                actividadService.validarDatos("Título", "x", "Rocha", 60, new BigDecimal("-1"), 0));
        assertNull(actividadService.validarDatos("Título", "x", "Rocha", 60, BigDecimal.ZERO, 0));
    }

    @Test
    void noSePuedeEditarUnaActividadConReservas() {
        prestadorLogueado();
        Actividad actividad = actividadPropia();
        when(actividadRepository.findById(10L)).thenReturn(Optional.of(actividad));
        when(reservaRepository.actividadTieneReservas(10L)).thenReturn(true);

        String error = actividadService.editarActividad(PRESTADOR_ID, 10L, "Nuevo", "x", "Rocha", null,
                60, BigDecimal.TEN, null, 0, null, null);

        assertEquals("La actividad ya tiene reservas: no se puede modificar. Podés darla de baja o agregarle horarios.", error);
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
                "Rocha", null, 60, BigDecimal.TEN, null, 0, null, null));
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
        when(horarioRepository.existsById(any(HorarioId.class))).thenReturn(false);
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
        when(horarioRepository.existsById(new HorarioId(10L, fecha, hora))).thenReturn(true);
        when(reservaRepository.horarioTieneReservas(10L, fecha, hora)).thenReturn(true);

        assertEquals("Ese horario ya tiene reservas: no se puede eliminar.",
                actividadService.eliminarHorario(PRESTADOR_ID, 10L, fecha, hora));
        verify(horarioRepository, never()).deleteById(any());
    }

    @Test
    void reservasRecibidasSonLasDeSuEmprendimiento() {
        prestadorLogueado();
        List<Reserva> reservas = List.of(new Reserva());
        when(reservaRepository.reservasDelEmprendimiento(3L)).thenReturn(reservas);

        assertSame(reservas, actividadService.reservasRecibidas(PRESTADOR_ID));
    }
}
