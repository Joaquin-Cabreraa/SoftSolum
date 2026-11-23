package uy.edu.um.proytic2026.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.pagos.PasarelaPago;
import uy.edu.um.proytic2026.pagos.ResultadoPago;
import uy.edu.um.proytic2026.pagos.SolicitudPago;
import uy.edu.um.proytic2026.repository.PagoRepository;
import uy.edu.um.proytic2026.repository.ReservaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Más reglas de PagoService (CU-T07), sobre las que ya cubre PagoServiceTest:
// reservas que no se pueden pagar, qué queda guardado en cada intento, pagos en revisión,
// reservas que vencen mientras se cobra, reintentos y reembolsos. La pasarela es un mock.
@ExtendWith(MockitoExtension.class)
class PagoReglasTest {

    @Mock private PagoRepository pagoRepository;
    @Mock private ReservaRepository reservaRepository;
    @Mock private PasarelaPago pasarela;
    @Mock private ConfirmacionPagoService confirmacionPagoService;

    @InjectMocks private PagoService pagoService;

    private static final Long TURISTA_ID = 1L;
    private Reserva reserva;

    private final SolicitudPago datosDelNavegador = new SolicitudPago(null, null, null, null,
            "tok_123", "visa", "1", 1, "ana@mail.com", "CI", "12345672", "APRO", "3704");

    @BeforeEach
    void preparar() {
        Turista turista = new Turista();
        turista.setId(TURISTA_ID);
        Actividad actividad = new Actividad();
        actividad.setTitulo("Kayak");
        Horario horario = new Horario();
        horario.setActividad(actividad);
        horario.setFecha(LocalDate.now().plusDays(2));
        horario.setHoraInicio(LocalTime.of(10, 0));

        reserva = new Reserva();
        reserva.setId(15L);
        reserva.setTurista(turista);
        reserva.setHorario(horario);
        reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
        reserva.setFechaCreacion(LocalDateTime.now().minusMinutes(10));
        reserva.setMontoTotal(new BigDecimal("3000"));      // 1500 x 2 cupos (lo calcula ReservaService)
    }

    private void reservaPagable() {
        when(reservaRepository.findById(15L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findFirstByReservaIdAndEstado(15L, EstadoPago.APROBADO)).thenReturn(Optional.empty());
    }

    // ---------- Reservas que NO se pagan ----------

    @Test
    void noSePagaUnaReservaCancelada() {
        reserva.setEstado(EstadoReserva.CANCELADA);
        when(reservaRepository.findById(15L)).thenReturn(Optional.of(reserva));

        PagoService.RespuestaPago r = pagoService.pagar(TURISTA_ID, 15L, datosDelNavegador);

        assertNull(r.estado());
        assertEquals("La reserva está cancelada.", r.mensaje());
        verifyNoInteractions(pasarela);
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void noSePagaDosVecesUnaReservaConfirmada() {
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        when(reservaRepository.findById(15L)).thenReturn(Optional.of(reserva));

        PagoService.RespuestaPago r = pagoService.pagar(TURISTA_ID, 15L, datosDelNavegador);

        assertNull(r.estado());
        assertEquals("Esta reserva ya está paga.", r.mensaje());
        verifyNoInteractions(pasarela);
    }

    @Test
    void noSePagaUnaReservaEnListaDeEspera() {
        reserva.setEstado(EstadoReserva.EN_ESPERA);
        when(reservaRepository.findById(15L)).thenReturn(Optional.of(reserva));

        PagoService.RespuestaPago r = pagoService.pagar(TURISTA_ID, 15L, datosDelNavegador);

        assertNull(r.estado());
        verifyNoInteractions(pasarela);
    }

    @Test
    void noSeCobraSiYaHayUnPagoAprobadoRegistrado() {
        when(reservaRepository.findById(15L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findFirstByReservaIdAndEstado(15L, EstadoPago.APROBADO)).thenReturn(Optional.of(new Pago()));

        PagoService.RespuestaPago r = pagoService.pagar(TURISTA_ID, 15L, datosDelNavegador);

        assertNull(r.estado());
        assertEquals("Esta reserva ya está paga.", r.mensaje());
        verifyNoInteractions(pasarela);
    }

    // ---------- Qué queda guardado en cada intento (RNE #14) ----------

    @Test
    void elPagoAprobadoGuardaMontoUltimosDigitosYCodigoDeLaPasarela() {
        reservaPagable();
        when(confirmacionPagoService.confirmarReserva(15L)).thenReturn(true);
        when(pasarela.cobrar(any())).thenReturn(new ResultadoPago(EstadoPago.APROBADO, "MP-1", "accredited", "visa", "4111111111113704"));

        pagoService.pagar(TURISTA_ID, 15L, datosDelNavegador);

        ArgumentCaptor<Pago> guardado = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(guardado.capture());
        Pago pago = guardado.getValue();
        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        assertEquals(new BigDecimal("3000"), pago.getMonto());       // precio x cupos de la reserva
        assertEquals("MP-1", pago.getIdExterno());
        assertEquals("3704", pago.getUltimosDigitos());              // solo los últimos 4: nunca la tarjeta entera
        assertSame(reserva, pago.getReserva());
        assertNotNull(pago.getFecha());
    }

    @Test
    void siLaPasarelaNoRespondeElIntentoQuedaGuardadoComoErrorYNoSeConfirma() {
        reservaPagable();
        when(pasarela.cobrar(any())).thenThrow(new IllegalStateException("timeout"));

        PagoService.RespuestaPago r = assertDoesNotThrow(() -> pagoService.pagar(TURISTA_ID, 15L, datosDelNavegador));

        assertEquals(EstadoPago.ERROR, r.estado());
        ArgumentCaptor<Pago> guardado = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(guardado.capture());
        assertEquals(EstadoPago.ERROR, guardado.getValue().getEstado());
        assertEquals(EstadoReserva.PENDIENTE_PAGO, reserva.getEstado());
        verify(reservaRepository, never()).save(any());              // la reserva no se tocó
    }

    @Test
    void unPagoEnRevisionNoConfirmaLaReserva() {
        reservaPagable();
        when(pasarela.cobrar(any())).thenReturn(new ResultadoPago(EstadoPago.PENDIENTE, "MP-3", "pending_contingency", "visa", "3704"));

        PagoService.RespuestaPago r = pagoService.pagar(TURISTA_ID, 15L, datosDelNavegador);

        assertEquals(EstadoPago.PENDIENTE, r.estado());
        assertFalse(r.aprobado());
        assertEquals(EstadoReserva.PENDIENTE_PAGO, reserva.getEstado());
        verify(reservaRepository, never()).save(any());
    }

    // ---------- Reintentos y vencimiento ----------

    @Test
    void cadaReintentoUsaOtraClaveDeIdempotencia() {
        reservaPagable();
        when(pagoRepository.countByReservaId(15L)).thenReturn(1L);   // ya hubo un intento
        when(pasarela.cobrar(any())).thenReturn(new ResultadoPago(EstadoPago.RECHAZADO, "MP-4", "cc_rejected_other_reason", "visa", "3704"));

        pagoService.pagar(TURISTA_ID, 15L, datosDelNavegador);

        ArgumentCaptor<SolicitudPago> enviada = ArgumentCaptor.forClass(SolicitudPago.class);
        verify(pasarela).cobrar(enviada.capture());
        assertEquals("umnatural-reserva-15-intento-2", enviada.getValue().claveIdempotencia());
    }

    @Test
    void siLaReservaVenceMientrasSeCobraSeDevuelveElDinero() {
        reservaPagable();
        // Mientras la pasarela procesa el cobro, la reserva se cancela (venció el plazo)
        when(pasarela.cobrar(any())).thenAnswer(inv -> {
            reserva.setEstado(EstadoReserva.CANCELADA);
            return new ResultadoPago(EstadoPago.APROBADO, "MP-9", "accredited", "visa", "3704");
        });

        PagoService.RespuestaPago r = pagoService.pagar(TURISTA_ID, 15L, datosDelNavegador);

        assertEquals(EstadoPago.ERROR, r.estado());
        assertTrue(r.mensaje().contains("Se te devolvió el dinero"));
        assertEquals(EstadoReserva.CANCELADA, reserva.getEstado());   // NO se confirma
        verify(pasarela).reembolsar(eq("MP-9"), eq(new BigDecimal("3000")), anyString());
    }

    @Test
    void elUltimoIntentoEsElMasReciente() {
        Pago reciente = new Pago();
        Pago viejo = new Pago();
        when(pagoRepository.findByReservaIdOrderByFechaDesc(15L)).thenReturn(List.of(reciente, viejo));
        assertSame(reciente, pagoService.ultimoIntento(15L));

        when(pagoRepository.findByReservaIdOrderByFechaDesc(16L)).thenReturn(List.of());
        assertNull(pagoService.ultimoIntento(16L));
    }

    // ---------- Reembolso ----------

    private Pago pagoAprobado() {
        Pago pago = new Pago();
        pago.setId(7L);
        pago.setIdExterno("MP-1");
        pago.setEstado(EstadoPago.APROBADO);
        pago.setMonto(new BigDecimal("3000"));
        when(pagoRepository.findFirstByReservaIdAndEstado(15L, EstadoPago.APROBADO)).thenReturn(Optional.of(pago));
        return pago;
    }

    @Test
    void reembolsoParcialDevuelveElMontoPedidoYLoRegistra() {
        Pago pago = pagoAprobado();
        when(pasarela.reembolsar("MP-1", new BigDecimal("1500"), "umnatural-reembolso-7")).thenReturn(true);

        String error = pagoService.reembolsar(reserva, new BigDecimal("1500"));     // 50 % de 3000

        assertNull(error);
        assertEquals(new BigDecimal("1500"), pago.getMontoReembolsado());
        verify(pagoRepository).save(pago);
    }

    @Test
    void reembolsoSinPagoAprobadoNoHaceNada() {
        when(pagoRepository.findFirstByReservaIdAndEstado(15L, EstadoPago.APROBADO)).thenReturn(Optional.empty());

        assertNull(pagoService.reembolsar(reserva, new BigDecimal("3000")));
        verifyNoInteractions(pasarela);
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void reembolsoDeCeroNoLlamaALaPasarela() {
        pagoAprobado();

        assertNull(pagoService.reembolsar(reserva, BigDecimal.ZERO));
        verify(pasarela, never()).reembolsar(anyString(), any(), anyString());
    }

    @Test
    void siLaPasarelaRechazaElReembolsoNoSeRegistraNada() {
        pagoAprobado();
        when(pasarela.reembolsar(anyString(), any(), anyString())).thenReturn(false);

        String error = pagoService.reembolsar(reserva, new BigDecimal("3000"));

        assertEquals("No se pudo hacer el reembolso con la pasarela. Probá de nuevo en un rato.", error);
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void siLaPasarelaSeCaeAlReembolsarDevuelveErrorYNoRompe() {
        pagoAprobado();
        when(pasarela.reembolsar(anyString(), any(), anyString())).thenThrow(new IllegalStateException("caída"));

        String error = assertDoesNotThrow(() -> pagoService.reembolsar(reserva, new BigDecimal("3000")));

        assertNotNull(error);
        verify(pagoRepository, never()).save(any());
    }
}
