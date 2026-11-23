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
import org.springframework.context.ApplicationEventPublisher;

// Tests unitarios de ReservaService (CU-T06 Reservar, CU-S01 Vencimiento).
// No usan la base de datos: los repositorios son "mocks" (objetos falsos de Mockito)
// a los que les decimos qué devolver en cada caso.
@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock private ReservaRepository reservaRepository;
    @Mock private HorarioRepository horarioRepository;
    @Mock private TuristaRepository turistaRepository;
    @Mock private CatalogoService catalogoService;
    @Mock private PagoService pagoService;
    @Mock private ApplicationEventPublisher eventPublisher;

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
                List.of("1.234.567-2", "45678905"), List.of("1999-03-02", "2000-01-01"), "  Una persona es celíaca ");

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
        // Creamos un turista con correo para la notificación.
        Turista turista = new Turista();
        turista.setUserEmail("turista@test.com");

        Reserva vencida = new Reserva();
        vencida.setTurista(turista);
        vencida.setHorario(horario);
        vencida.setCantCupos(2);
        vencida.setEstado(EstadoReserva.PENDIENTE_PAGO);
        vencida.setFechaCreacion(LocalDateTime.now().minusHours(3));

        when(reservaRepository.pendientesConPlazoVencido(
                eq(EstadoReserva.PENDIENTE_PAGO),
                any(LocalDateTime.class)
        )).thenReturn(List.of(vencida));

        int canceladas = reservaService.cancelarReservasVencidas();

        assertEquals(1, canceladas);
        assertEquals(EstadoReserva.CANCELADA, vencida.getEstado());
        verify(horarioRepository).devolverCupos(ACTIVIDAD_ID, fecha, hora, 2);

        // Comprueba que se buscan las reservas cuyo plazo venció.
        ArgumentCaptor<LocalDateTime> limite =
                ArgumentCaptor.forClass(LocalDateTime.class);

        verify(reservaRepository).pendientesConPlazoVencido(
                eq(EstadoReserva.PENDIENTE_PAGO),
                limite.capture()
        );

        assertTrue(limite.getValue().isBefore(LocalDateTime.now().minusMinutes(119)));
    }


    // ---------- Lista de espera ----------

    private Reserva reservaDe(Long id, Long turistaId, int cupos, EstadoReserva estado) {
        Turista t = new Turista();
        t.setId(turistaId);
        Reserva r = new Reserva();
        r.setId(id);
        r.setTurista(t);
        r.setHorario(horario);
        r.setCantCupos(cupos);
        r.setEstado(estado);
        r.setFechaCreacion(LocalDateTime.now().minusMinutes(30));
        r.setMontoTotal(new BigDecimal("1500").multiply(BigDecimal.valueOf(cupos)));
        return r;
    }

    @Test
    void listaDeEsperaNoSePermiteSiTodaviaHayLugar() {
        horarioReservable();   // quedan 3 cupos

        String error = reservaService.validarListaDeEspera(TURISTA_ID, ACTIVIDAD_ID, fecha, hora, 2,
                List.of("Ana", "Juan"), List.of("P", "G"), List.of("12345672", "45678905"), List.of("1999-03-02", "2000-01-01"));

        assertEquals("Todavía hay lugar en este horario: podés reservar directamente.", error);
    }

    @Test
    void anotarseEnListaDeEsperaNoDescuentaCupos() {
        horario.setCuposDisponibles(0);   // horario lleno
        horarioReservable();
        Turista turista = new Turista();
        turista.setId(TURISTA_ID);
        when(turistaRepository.findById(TURISTA_ID)).thenReturn(Optional.of(turista));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        Reserva reserva = reservaService.anotarEnListaDeEspera(TURISTA_ID, ACTIVIDAD_ID, fecha, hora, 2,
                List.of("Ana", "Juan"), List.of("P", "G"), List.of("12345672", "45678905"),
                List.of("1999-03-02", "2000-01-01"), null);

        assertNotNull(reserva);
        assertEquals(EstadoReserva.EN_ESPERA, reserva.getEstado());
        verify(horarioRepository, never()).descontarCupos(anyLong(), any(), any(), anyInt());
    }

    @Test
    void alCancelarUnaPendienteElLugarPasaAlPrimeroDeLaListaDeEspera() {
        Reserva pendiente = reservaDe(1L, TURISTA_ID, 2, EstadoReserva.PENDIENTE_PAGO);
        Reserva enEspera = reservaDe(2L, 99L, 2, EstadoReserva.EN_ESPERA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(pendiente));
        when(reservaRepository.delHorarioEnEstado(EstadoReserva.EN_ESPERA, ACTIVIDAD_ID, fecha, hora)).thenReturn(List.of(enEspera));
        when(catalogoService.sePuedeReservar(eq(horario), any(LocalDateTime.class))).thenReturn(true);
        when(horarioRepository.descontarCupos(ACTIVIDAD_ID, fecha, hora, 2)).thenReturn(1);

        String error = reservaService.cancelarReserva(TURISTA_ID, 1L);

        assertNull(error);
        assertEquals(EstadoReserva.CANCELADA, pendiente.getEstado());
        verify(horarioRepository).devolverCupos(ACTIVIDAD_ID, fecha, hora, 2);
        assertEquals(EstadoReserva.PENDIENTE_PAGO, enEspera.getEstado());   // ahora tiene que pagar
        assertNotNull(enEspera.getPlazoPagoDesde());                       // y las 2 horas corren desde ya
    }

    @Test
    void siElPrimeroDeLaFilaNoEntraSeLeOfreceAlSiguiente() {
        Reserva grupoGrande = reservaDe(5L, 7L, 4, EstadoReserva.EN_ESPERA);
        Reserva grupoChico = reservaDe(6L, 8L, 1, EstadoReserva.EN_ESPERA);
        when(reservaRepository.delHorarioEnEstado(EstadoReserva.EN_ESPERA, ACTIVIDAD_ID, fecha, hora))
                .thenReturn(List.of(grupoGrande, grupoChico));
        when(catalogoService.sePuedeReservar(eq(horario), any(LocalDateTime.class))).thenReturn(true);
        when(horarioRepository.descontarCupos(ACTIVIDAD_ID, fecha, hora, 4)).thenReturn(0);   // no entran 4
        when(horarioRepository.descontarCupos(ACTIVIDAD_ID, fecha, hora, 1)).thenReturn(1);   // 1 sí

        int promovidas = reservaService.promoverListaDeEspera(ACTIVIDAD_ID, fecha, hora);

        assertEquals(1, promovidas);
        assertEquals(EstadoReserva.EN_ESPERA, grupoGrande.getEstado());
        assertEquals(EstadoReserva.PENDIENTE_PAGO, grupoChico.getEstado());
    }

    @Test
    void noSePuedeCancelarUnaReservaAjena() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaDe(1L, 50L, 1, EstadoReserva.PENDIENTE_PAGO)));

        assertEquals("La reserva no existe.", reservaService.cancelarReserva(TURISTA_ID, 1L));
        verify(horarioRepository, never()).devolverCupos(anyLong(), any(), any(), anyInt());
    }

    @Test
    void cancelarUnaConfirmadaATiempoDevuelveElPorcentajeDelPrestador() {
        horario.setPorcentajeReembolso(50);
        horario.setMinMinutosCancelacion(60);
        Reserva confirmada = reservaDe(1L, TURISTA_ID, 2, EstadoReserva.CONFIRMADA);   // pagó 3000
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(confirmada));
        when(pagoService.reembolsar(confirmada, new BigDecimal("1500.00"))).thenReturn(null);

        assertNull(reservaService.cancelarReserva(TURISTA_ID, 1L));
        assertEquals(EstadoReserva.CANCELADA, confirmada.getEstado());
        verify(horarioRepository).devolverCupos(ACTIVIDAD_ID, fecha, hora, 2);
    }

    @Test
    void noSeCancelaUnaConfirmadaSiElHorarioNoLoPermite() {
        horario.setCancelable(false);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaDe(1L, TURISTA_ID, 2, EstadoReserva.CONFIRMADA)));

        assertEquals("Este horario no admite cancelación.", reservaService.cancelarReserva(TURISTA_ID, 1L));
        verifyNoInteractions(pagoService);
    }

    // ---------- Descuento de última hora ----------

    @Test
    void conDescuentoDeUltimaHoraElMontoEsMenor() {
        // Horario que empieza en 2 horas, con 20 % de descuento en las 3 horas previas
        LocalDateTime inicio = LocalDateTime.now().plusHours(2).withSecond(0).withNano(0);
        horario.setFecha(inicio.toLocalDate());
        horario.setHoraInicio(inicio.toLocalTime());
        horario.getActividad().setDescuentoUltimaHora(20);
        horario.getActividad().setHorasUltimaHora(3);
        when(turistaRepository.existsById(TURISTA_ID)).thenReturn(true);
        when(horarioRepository.findById(new HorarioId(ACTIVIDAD_ID, inicio.toLocalDate(), inicio.toLocalTime())))
                .thenReturn(Optional.of(horario));
        when(catalogoService.estaPublicada(horario.getActividad())).thenReturn(true);
        when(catalogoService.sePuedeReservar(eq(horario), any(LocalDateTime.class))).thenReturn(true);
        when(horarioRepository.descontarCupos(ACTIVIDAD_ID, inicio.toLocalDate(), inicio.toLocalTime(), 2)).thenReturn(1);
        Turista turista = new Turista();
        turista.setId(TURISTA_ID);
        when(turistaRepository.findById(TURISTA_ID)).thenReturn(Optional.of(turista));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        Reserva reserva = reservaService.crearReserva(TURISTA_ID, ACTIVIDAD_ID, inicio.toLocalDate(), inicio.toLocalTime(), 2,
                List.of("Ana", "Juan"), List.of("P", "G"), List.of("12345672", "45678905"),
                List.of("1999-03-02", "2000-01-01"), null);

        assertEquals(new BigDecimal("2400"), reserva.getMontoTotal());   // 1500 - 20 % = 1200, x 2
    }

    // ---------- Una persona, un lugar ----------

    @Test
    void noSePuedeReservarDosVecesElMismoHorario() {
        horarioReservable();
        when(reservaRepository.turistaTieneReservaEnHorario(TURISTA_ID, ACTIVIDAD_ID, fecha, hora, EstadoReserva.CANCELADA))
                .thenReturn(true);

        String error = reservaService.validarReserva(TURISTA_ID, ACTIVIDAD_ID, fecha, hora, 1,
                List.of("Ana"), List.of("Pérez"), List.of("12345672"), List.of("1999-03-02"));

        assertEquals("Ya tenés una reserva para este horario. La podés ver en Mis reservas.", error);
    }

    @Test
    void unaPersonaQueYaTieneLugarNoPuedeOcuparOtro() {
        horarioReservable();
        when(reservaRepository.documentosEnHorario(ACTIVIDAD_ID, fecha, hora, EstadoReserva.CANCELADA))
                .thenReturn(List.of("12345672"));

        String error = reservaService.validarReserva(TURISTA_ID, ACTIVIDAD_ID, fecha, hora, 1,
                List.of("Ana"), List.of("Pérez"), List.of("1.234.567-2"), List.of("1999-03-02"));

        assertEquals("La persona con documento 12345672 ya tiene un lugar en este horario.", error);
    }

    @Test
    void rechazaUnaCedulaConDigitoVerificadorMal() {
        horarioReservable();

        String error = reservaService.validarReserva(TURISTA_ID, ACTIVIDAD_ID, fecha, hora, 1,
                List.of("Ana"), List.of("Pérez"), List.of("1.234.567-3"), List.of("1999-03-02"));

        assertTrue(error.contains("no es válida"));
    }

    // ---------- Política de cancelación ----------

    @Test
    void nadaSeCancelaUnaVezQueEmpezoLaActividad() {
        horario.setFecha(LocalDate.now().minusDays(1));   // ya fue
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaDe(1L, TURISTA_ID, 2, EstadoReserva.PENDIENTE_PAGO)));

        assertEquals("La actividad ya empezó: no se puede cancelar.", reservaService.cancelarReserva(TURISTA_ID, 1L));
        verify(horarioRepository, never()).devolverCupos(anyLong(), any(), any(), anyInt());
    }

    @Test
    void conPoliticaSinReembolsoSeCancelaPeroNoSeDevuelvePlata() {
        horario.setPorcentajeReembolso(0);
        horario.setMinMinutosCancelacion(60);
        Reserva confirmada = reservaDe(1L, TURISTA_ID, 2, EstadoReserva.CONFIRMADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(confirmada));
        when(pagoService.reembolsar(confirmada, BigDecimal.ZERO)).thenReturn(null);

        assertNull(reservaService.cancelarReserva(TURISTA_ID, 1L));
        assertEquals(EstadoReserva.CANCELADA, confirmada.getEstado());
        verify(horarioRepository).devolverCupos(ACTIVIDAD_ID, fecha, hora, 2);   // el lugar queda libre igual
    }

    @Test
    void fueraDelPlazoQueEligioElPrestadorNoSeCancela() {
        horario.setPorcentajeReembolso(100);
        horario.setMinMinutosCancelacion(5 * 24 * 60);   // hasta 5 días antes, y faltan 3
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaDe(1L, TURISTA_ID, 2, EstadoReserva.CONFIRMADA)));

        assertEquals("Ya pasó el plazo para cancelar esta reserva.", reservaService.cancelarReserva(TURISTA_ID, 1L));
        verifyNoInteractions(pagoService);
    }

    @Test
    void siElPrestadorCancelaLaSalidaSeDevuelveElCienPorCientoATodos() {
        Reserva paga = reservaDe(1L, TURISTA_ID, 2, EstadoReserva.CONFIRMADA);         // pagó 3000
        Reserva pendiente = reservaDe(2L, 50L, 1, EstadoReserva.PENDIENTE_PAGO);
        when(reservaRepository.delHorarioNoCanceladas(ACTIVIDAD_ID, fecha, hora, EstadoReserva.CANCELADA))
                .thenReturn(List.of(paga, pendiente));
        when(pagoService.reembolsar(paga, new BigDecimal("3000"))).thenReturn(null);

        int sinReembolsar = reservaService.cancelarPorPrestador(horario, "Tormenta");

        assertEquals(0, sinReembolsar);
        assertEquals(EstadoReserva.CANCELADA, paga.getEstado());
        assertEquals(EstadoReserva.CANCELADA, pendiente.getEstado());
        assertEquals("Tormenta", paga.getMotivoCancelacionPrestador());
        verify(pagoService).reembolsar(paga, new BigDecimal("3000"));      // el 100 %
        verify(horarioRepository, never()).devolverCupos(anyLong(), any(), any(), anyInt());   // la salida no se ofrece a nadie más
    }
}
