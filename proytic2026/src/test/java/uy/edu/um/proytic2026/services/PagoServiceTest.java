package uy.edu.um.proytic2026.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.pasarela.PasarelaClient;
import uy.edu.um.proytic2026.pasarela.RespuestaPasarela;
import uy.edu.um.proytic2026.pasarela.SolicitudPasarela;
import uy.edu.um.proytic2026.repository.PagoRepository;
import uy.edu.um.proytic2026.repository.ReservaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Tests unitarios de PagoService (CU-T07 Pagar reserva). Sin base de datos ni red:
// el repositorio y la pasarela son mocks de Mockito.
@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock private PagoRepository pagoRepository;
    @Mock private ReservaRepository reservaRepository;
    @Mock private PasarelaClient pasarelaClient;

    @InjectMocks private PagoService pagoService;

    private static final Long TURISTA_ID = 1L;
    private static final Long RESERVA_ID = 50L;
    private static final String TARJETA = "4111 1111 1111 1111";

    private final String vencimiento = String.format("%02d/%02d",
            YearMonth.now().plusYears(2).getMonthValue(), YearMonth.now().plusYears(2).getYear() % 100);

    private Reserva reserva;

    @BeforeEach
    void prepararDatos() {
        Actividad actividad = new Actividad();
        actividad.setId(10L);
        actividad.setPrecio(new BigDecimal("1500"));

        Horario horario = new Horario();
        horario.setActividad(actividad);

        Turista turista = new Turista();
        turista.setId(TURISTA_ID);

        reserva = new Reserva();
        reserva.setId(RESERVA_ID);
        reserva.setTurista(turista);
        reserva.setHorario(horario);
        reserva.setCantCupos(2);
        reserva.setMontoTotal(actividad.getPrecio().multiply(BigDecimal.valueOf(2)));   // precio x cupos = 3000
        reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
        reserva.setFechaCreacion(LocalDateTime.now().minusMinutes(10));
    }

    private void reservaBloqueable() {
        when(reservaRepository.findByIdParaPagar(RESERVA_ID)).thenReturn(Optional.of(reserva));
    }

    private void pagoSeGuarda() {
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Pago pagar() {
        return pagoService.pagar(TURISTA_ID, RESERVA_ID, TARJETA, vencimiento, "123");
    }

    // ---------- Resultados de la pasarela ----------

    @Test
    void pagoAprobadoConfirmaLaReserva() {
        reservaBloqueable();
        pagoSeGuarda();
        when(pasarelaClient.cobrar(any())).thenReturn(new RespuestaPasarela("APROBADO", "PSR-1", "Pago aprobado"));

        Pago pago = pagar();

        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        assertEquals(EstadoReserva.CONFIRMADA, reserva.getEstado());
        assertEquals("1111", pago.getUltimos4());
        assertEquals("PSR-1", pago.getCodigoPasarela());
        assertEquals(TipoPago.COBRO, pago.getTipo());
        verify(reservaRepository).save(reserva);
    }

    @Test
    void pagoRechazadoNoConfirmaYLaReservaSigueDisponibleParaReintentar() {
        reservaBloqueable();
        pagoSeGuarda();
        when(pasarelaClient.cobrar(any())).thenReturn(new RespuestaPasarela("RECHAZADO", "PSR-2", "Fondos insuficientes"));

        Pago pago = pagar();

        assertEquals(EstadoPago.RECHAZADO, pago.getEstado());
        assertEquals("Fondos insuficientes", pago.getDetalle());
        assertEquals(EstadoReserva.PENDIENTE_PAGO, reserva.getEstado());
        verify(reservaRepository, never()).save(any());
        assertTrue(pagoService.sePuedePagar(reserva));       // se puede reintentar
    }

    @Test
    void timeoutNoConfirmaNoRompeYQuedaGuardadoComoError() {
        reservaBloqueable();
        pagoSeGuarda();
        when(pasarelaClient.cobrar(any())).thenThrow(new ResourceAccessException("Read timed out"));

        Pago pago = assertDoesNotThrow(this::pagar);

        assertEquals(EstadoPago.ERROR, pago.getEstado());
        assertNull(pago.getCodigoPasarela());
        assertEquals(EstadoReserva.PENDIENTE_PAGO, reserva.getEstado());
        verify(pagoRepository).save(pago);                    // el intento quedó registrado
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void pasarelaCaidaOConRespuestaRaraNoConfirma() {
        reservaBloqueable();
        pagoSeGuarda();
        when(pasarelaClient.cobrar(any())).thenReturn(null);   // body vacío

        Pago pago = pagar();

        assertEquals(EstadoPago.ERROR, pago.getEstado());
        assertEquals(EstadoReserva.PENDIENTE_PAGO, reserva.getEstado());
    }

    @Test
    void despuesDeUnRechazoSePuedeReintentarYConfirmar() {
        reservaBloqueable();
        pagoSeGuarda();
        when(pasarelaClient.cobrar(any()))
                .thenReturn(new RespuestaPasarela("RECHAZADO", "PSR-2", "Fondos insuficientes"))
                .thenReturn(new RespuestaPasarela("APROBADO", "PSR-3", "Pago aprobado"));

        pagar();
        Pago segundo = pagar();

        assertEquals(EstadoPago.APROBADO, segundo.getEstado());
        assertEquals(EstadoReserva.CONFIRMADA, reserva.getEstado());
        assertEquals(2, reserva.getPagos().size());           // cada intento queda guardado (RNE #14)
        assertSame(segundo, pagoService.ultimoCobro(reserva));
    }

    // ---------- Monto ----------

    @Test
    void elMontoCobradoEsPrecioPorCupos() {
        reservaBloqueable();
        pagoSeGuarda();
        when(pasarelaClient.cobrar(any())).thenReturn(new RespuestaPasarela("APROBADO", "PSR-1", "ok"));

        Pago pago = pagar();

        assertEquals(0, new BigDecimal("3000").compareTo(pago.getMonto()));     // 1500 x 2
        ArgumentCaptor<SolicitudPasarela> enviado = ArgumentCaptor.forClass(SolicitudPasarela.class);
        verify(pasarelaClient).cobrar(enviado.capture());
        assertEquals(0, new BigDecimal("3000").compareTo(enviado.getValue().monto()));
        assertEquals("4111111111111111", enviado.getValue().numeroTarjeta());   // sin espacios
    }

    // ---------- Reservas que no se pueden pagar ----------

    @Test
    void noSePagaUnaReservaCancelada() {
        reserva.setEstado(EstadoReserva.CANCELADA);
        reservaBloqueable();

        assertNull(pagar());
        verify(pasarelaClient, never()).cobrar(any());
        verify(pagoRepository, never()).save(any());
        assertEquals("Esta reserva está cancelada, ya no se puede pagar.",
                pagoService.validarReservaPagable(TURISTA_ID, reserva));
    }

    @Test
    void noSePagaUnaReservaVencida() {
        reserva.setFechaCreacion(LocalDateTime.now().minusHours(3));   // pasaron más de 2 h
        reservaBloqueable();

        assertNull(pagar());
        verify(pasarelaClient, never()).cobrar(any());
        verify(pagoRepository, never()).save(any());
        assertFalse(pagoService.sePuedePagar(reserva));
        assertEquals("Venció el plazo de 2 horas para pagar esta reserva.",
                pagoService.validarReservaPagable(TURISTA_ID, reserva));
    }

    @Test
    void noSePagaDosVecesUnaReservaConfirmada() {
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaBloqueable();

        assertNull(pagar());
        verify(pasarelaClient, never()).cobrar(any());
    }

    @Test
    void noSePuedePagarLaReservaDeOtroTurista() {
        reservaBloqueable();

        assertNull(pagoService.pagar(99L, RESERVA_ID, TARJETA, vencimiento, "123"));
        verify(pasarelaClient, never()).cobrar(any());
    }

    @Test
    void noLlamaALaPasarelaSiLaTarjetaTieneFormatoInvalido() {
        assertNotNull(pagoService.validarTarjeta("1234", vencimiento, "123"));
        assertNotNull(pagoService.validarTarjeta(TARJETA, "13/30", "123"));
        assertNotNull(pagoService.validarTarjeta(TARJETA, "01/20", "123"));      // vencida
        assertNotNull(pagoService.validarTarjeta(TARJETA, vencimiento, "12"));
        assertNull(pagoService.validarTarjeta(TARJETA, vencimiento, "123"));

        reservaBloqueable();
        assertNull(pagoService.pagar(TURISTA_ID, RESERVA_ID, "abc", vencimiento, "123"));
        verify(pasarelaClient, never()).cobrar(any());
    }

    // ---------- Reembolso ----------

    @Test
    void reembolsoRegistraUnPagoDeDevolucionConElPorcentaje() {
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        Pago cobro = new Pago();
        cobro.setMonto(new BigDecimal("3000"));
        cobro.setUltimos4("1111");
        when(pagoRepository.findFirstByReservaIdAndTipoAndEstado(RESERVA_ID, TipoPago.COBRO, EstadoPago.APROBADO))
                .thenReturn(Optional.of(cobro));
        when(pagoRepository.existsByReservaIdAndTipoAndEstado(RESERVA_ID, TipoPago.REEMBOLSO, EstadoPago.APROBADO))
                .thenReturn(false);
        pagoSeGuarda();

        Pago devolucion = pagoService.reembolsar(reserva, 50);

        assertEquals(TipoPago.REEMBOLSO, devolucion.getTipo());
        assertEquals(EstadoPago.APROBADO, devolucion.getEstado());
        assertEquals(0, new BigDecimal("1500").compareTo(devolucion.getMonto()));   // 50% de 3000
        assertEquals("1111", devolucion.getUltimos4());
        assertSame(reserva, devolucion.getReserva());
    }

    @Test
    void reembolsoNoHaceNadaSiNuncaSePagoOYaSeDevolvio() {
        when(pagoRepository.findFirstByReservaIdAndTipoAndEstado(RESERVA_ID, TipoPago.COBRO, EstadoPago.APROBADO))
                .thenReturn(Optional.empty());
        assertNull(pagoService.reembolsar(reserva, 100));

        Pago cobro = new Pago();
        cobro.setMonto(new BigDecimal("3000"));
        when(pagoRepository.findFirstByReservaIdAndTipoAndEstado(RESERVA_ID, TipoPago.COBRO, EstadoPago.APROBADO))
                .thenReturn(Optional.of(cobro));
        when(pagoRepository.existsByReservaIdAndTipoAndEstado(RESERVA_ID, TipoPago.REEMBOLSO, EstadoPago.APROBADO))
                .thenReturn(true);
        assertNull(pagoService.reembolsar(reserva, 100));

        verify(pagoRepository, never()).save(any());
    }

    @Test
    void reembolsoRechazaPorcentajesInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> pagoService.reembolsar(reserva, -1));
        assertThrows(IllegalArgumentException.class, () -> pagoService.reembolsar(reserva, 101));
        assertNull(pagoService.reembolsar(reserva, 0));
    }
}
