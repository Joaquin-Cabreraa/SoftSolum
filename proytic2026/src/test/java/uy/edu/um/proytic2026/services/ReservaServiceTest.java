package uy.edu.um.proytic2026.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.repository.HorarioRepository;
import uy.edu.um.proytic2026.repository.ReservaRepository;
import uy.edu.um.proytic2026.repository.TuristaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Tests unitarios de ReservaService (CU-T06 Reservar, CU-S01 Vencimiento).
// No usan la base de datos: los repositorios son "mocks" (objetos falsos de Mockito)
// a los que les decimos qué devolver en cada caso.
@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock private ReservaRepository reservaRepository;
    @Mock private HorarioRepository horarioRepository;
    @Mock private TuristaRepository turistaRepository;
    @Mock private CatalogoService catalogoService;

    @InjectMocks private ReservaService reservaService;

    private static final Long TURISTA_ID = 1L;
    private static final Long ACTIVIDAD_ID = 10L;
    private final LocalDate fecha = LocalDate.now().plusDays(3);
    private final LocalTime hora = LocalTime.of(18, 0);

    private Horario horario;

    @BeforeEach
    void prepararDatos() {
        Actividad actividad = new Actividad();
        actividad.setId(ACTIVIDAD_ID);
        actividad.setPrecio(new BigDecimal("1500"));
        actividad.setMinPreviosReservar(60);

        horario = new Horario();
        horario.setActividad(actividad);
        horario.setFecha(fecha);
        horario.setHoraInicio(hora);
        horario.setHoraFin(LocalTime.of(20, 0));
        horario.setCuposTotales(10);
        horario.setCuposDisponibles(3);
        horario.setCancelable(true);
    }

    // Deja todo listo para que la validación pase hasta el control de cupos
    private void horarioReservable() {
        when(turistaRepository.existsById(TURISTA_ID)).thenReturn(true);
        when(horarioRepository.findById(new HorarioId(ACTIVIDAD_ID, fecha, hora))).thenReturn(Optional.of(horario));
        when(catalogoService.estaPublicada(horario.getActividad())).thenReturn(true);
        when(catalogoService.sePuedeReservar(eq(horario), any(LocalDateTime.class))).thenReturn(true);
    }

    @Test
    void rechazaSiPideMasCuposQueLosDisponibles() {
        horarioReservable();

        String error = reservaService.validarReserva(TURISTA_ID, ACTIVIDAD_ID, fecha, hora, 4,
                List.of(), List.of(), List.of(), List.of());

        assertEquals("No hay suficientes cupos: quedan 3.", error);
    }

    @Test
    void rechazaSiYaPasoElPlazoParaReservar() {
        when(turistaRepository.existsById(TURISTA_ID)).thenReturn(true);
        when(horarioRepository.findById(any(HorarioId.class))).thenReturn(Optional.of(horario));
        when(catalogoService.estaPublicada(horario.getActividad())).thenReturn(true);
        when(catalogoService.sePuedeReservar(eq(horario), any(LocalDateTime.class))).thenReturn(false);

        String error = reservaService.validarReserva(TURISTA_ID, ACTIVIDAD_ID, fecha, hora, 1,
                List.of("Ana"), List.of("Pérez"), List.of("12345672"), List.of("1999-03-02"));

        assertEquals("Ya pasó el plazo para reservar este horario.", error);
    }

    @Test
    void rechazaSiFaltanDatosDeAlgunaPersona() {
        horarioReservable();

        // Pide 2 cupos pero manda datos de 1 sola persona
        String error = reservaService.validarReserva(TURISTA_ID, ACTIVIDAD_ID, fecha, hora, 2,
                List.of("Ana"), List.of("Pérez"), List.of("12345672"), List.of("1999-03-02"));

        assertEquals("Completá los datos de las 2 personas del grupo.", error);
    }

    @Test
    void rechazaDocumentosRepetidosEnElGrupo() {
        horarioReservable();

        String error = reservaService.validarReserva(TURISTA_ID, ACTIVIDAD_ID, fecha, hora, 2,
                List.of("Ana", "Juan"), List.of("Pérez", "Gómez"),
                List.of("1.234.567-2", "12345672"),          // el mismo documento escrito distinto
                List.of("1999-03-02", "2000-01-01"));

        assertEquals("El documento 12345672 está repetido en el grupo.", error);
    }

    @Test
    void rechazaSiNoEsTurista() {
        when(turistaRepository.existsById(99L)).thenReturn(false);

        String error = reservaService.validarReserva(99L, ACTIVIDAD_ID, fecha, hora, 1,
                List.of("Ana"), List.of("Pérez"), List.of("12345672"), List.of("1999-03-02"));

        assertEquals("Tenés que iniciar sesión como turista para reservar.", error);
    }

    @Test
    void reservaCorrectaDescuentaCuposYQuedaPendienteDePago() {
        horarioReservable();
        when(horarioRepository.descontarCupos(ACTIVIDAD_ID, fecha, hora, 2)).thenReturn(1);   // 1 fila modificada = ok
        Turista turista = new Turista();
        turista.setId(TURISTA_ID);
        when(turistaRepository.findById(TURISTA_ID)).thenReturn(Optional.of(turista));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        Reserva reserva = reservaService.crearReserva(TURISTA_ID, ACTIVIDAD_ID, fecha, hora, 2,
                List.of("Ana", "Juan"), List.of("Pérez", "Gómez"),
                List.of("1.234.567-2", "45678901"), List.of("1999-03-02", "2000-01-01"), "  Una persona es celíaca ");

        assertNotNull(reserva);
        verify(horarioRepository).descontarCupos(ACTIVIDAD_ID, fecha, hora, 2);
        assertEquals(EstadoReserva.PENDIENTE_PAGO, reserva.getEstado());
        assertEquals(new BigDecimal("3000"), reserva.getMontoTotal());          // 1500 x 2 (RNE #11)
        assertEquals(2, reserva.getParticipantes().size());
        assertEquals("12345672", reserva.getParticipantes().get(0).getCi());    // sin puntos ni guion
        assertEquals("Una persona es celíaca", reserva.getObservacion());
        assertSame(turista, reserva.getTurista());
        assertNotNull(reserva.getFechaCreacion());
    }

    @Test
    void noCreaLaReservaSiOtroTuristaSeLlevoLosCupos() {
        horarioReservable();
        // La validación pasa, pero el UPDATE atómico no modifica ninguna fila
        when(horarioRepository.descontarCupos(ACTIVIDAD_ID, fecha, hora, 1)).thenReturn(0);

        Reserva reserva = reservaService.crearReserva(TURISTA_ID, ACTIVIDAD_ID, fecha, hora, 1,
                List.of("Ana"), List.of("Pérez"), List.of("12345672"), List.of("1999-03-02"), null);

        assertNull(reserva);
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void noDescuentaCuposSiLosDatosNoSonValidos() {
        horarioReservable();

        Reserva reserva = reservaService.crearReserva(TURISTA_ID, ACTIVIDAD_ID, fecha, hora, 5,
                List.of(), List.of(), List.of(), List.of(), null);

        assertNull(reserva);
        verify(horarioRepository, never()).descontarCupos(anyLong(), any(), any(), anyInt());
    }

    @Test
    void cancelaLasReservasVencidasYDevuelveLosCupos() {
        Reserva vencida = new Reserva();
        vencida.setHorario(horario);
        vencida.setCantCupos(2);
        vencida.setEstado(EstadoReserva.PENDIENTE_PAGO);
        vencida.setFechaCreacion(LocalDateTime.now().minusHours(3));
        when(reservaRepository.findByEstadoAndFechaCreacionBefore(eq(EstadoReserva.PENDIENTE_PAGO), any(LocalDateTime.class)))
                .thenReturn(List.of(vencida));

        int canceladas = reservaService.cancelarReservasVencidas();

        assertEquals(1, canceladas);
        assertEquals(EstadoReserva.CANCELADA, vencida.getEstado());
        verify(horarioRepository).devolverCupos(ACTIVIDAD_ID, fecha, hora, 2);

        // Busca las pendientes creadas hace más de 2 horas
        ArgumentCaptor<LocalDateTime> limite = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reservaRepository).findByEstadoAndFechaCreacionBefore(eq(EstadoReserva.PENDIENTE_PAGO), limite.capture());
        assertTrue(limite.getValue().isBefore(LocalDateTime.now().minusMinutes(119)));
    }
}
