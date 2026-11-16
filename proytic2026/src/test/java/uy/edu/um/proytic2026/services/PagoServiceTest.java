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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Tests de PagoService (CU-T07). La pasarela es un mock: no se llama a Mercado Pago.
@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock private PagoRepository pagoRepository;
    @Mock private ReservaRepository reservaRepository;
    @Mock private PasarelaPago pasarela;

    @InjectMocks private PagoService pagoService;

    private static final Long TURISTA_ID = 1L;
    private Reserva reserva;

    // Lo que manda el navegador: el token de la tarjeta y el medio de pago (y un monto trucho que se ignora)
    private final SolicitudPago datosDelNavegador = new SolicitudPago(new BigDecimal("1"), null, null, null,
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
        reserva.setMontoTotal(new BigDecimal("3000"));
    }

    private void reservaPagable() {
        when(reservaRepository.findById(15L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findFirstByReservaIdAndEstado(15L, EstadoPago.APROBADO)).thenReturn(Optional.empty());
    }

    @Test
    void pagoAprobadoConfirmaLaReservaYCobraElMontoDeLaReserva() {
        reservaPagable();
        when(pasarela.cobrar(any())).thenReturn(new ResultadoPago(EstadoPago.APROBADO, "MP-1", "accredited", "visa", "3704"));

        PagoService.RespuestaPago r = pagoService.pagar(TURISTA_ID, 15L, datosDelNavegador);

        assertTrue(r.aprobado());
        assertEquals(EstadoReserva.CONFIRMADA, reserva.getEstado());
        ArgumentCaptor<SolicitudPago> enviada = ArgumentCaptor.forClass(SolicitudPago.class);
        verify(pasarela).cobrar(enviada.capture());
        assertEquals(new BigDecimal("3000"), enviada.getValue().monto());          // el del servidor, no el del navegador
        assertEquals("reserva-15", enviada.getValue().referencia());
        assertEquals("umnatural-reserva-15-intento-1", enviada.getValue().claveIdempotencia());
    }

    @Test
    void pagoRechazadoNoConfirmaYSePuedeReintentar() {
        reservaPagable();
        when(pasarela.cobrar(any())).thenReturn(new ResultadoPago(EstadoPago.RECHAZADO, "MP-2",
                "cc_rejected_insufficient_amount", "visa", "3704"));

        PagoService.RespuestaPago r = pagoService.pagar(TURISTA_ID, 15L, datosDelNavegador);

        assertFalse(r.aprobado());
        assertEquals("Pago rechazado: la tarjeta no tiene saldo suficiente.", r.mensaje());
        assertEquals(EstadoReserva.PENDIENTE_PAGO, reserva.getEstado());   // el cupo sigue guardado
        verify(pagoRepository).save(any(Pago.class));                      // queda registrado el intento
    }

    @Test
    void siLaPasarelaNoRespondeLaReservaSigueGuardada() {
        reservaPagable();
        when(pasarela.cobrar(any())).thenThrow(new IllegalStateException("timeout"));

        PagoService.RespuestaPago r = pagoService.pagar(TURISTA_ID, 15L, datosDelNavegador);

        assertEquals(EstadoPago.ERROR, r.estado());
        assertEquals(EstadoReserva.PENDIENTE_PAGO, reserva.getEstado());
    }

    @Test
    void noSePuedePagarUnaReservaAjena() {
        when(reservaRepository.findById(15L)).thenReturn(Optional.of(reserva));

        PagoService.RespuestaPago r = pagoService.pagar(99L, 15L, datosDelNavegador);

        assertNull(r.estado());
        verifyNoInteractions(pasarela);
    }

    @Test
    void noSePuedePagarSiVencieronLasDosHoras() {
        reserva.setFechaCreacion(LocalDateTime.now().minusHours(3));
        when(reservaRepository.findById(15L)).thenReturn(Optional.of(reserva));

        PagoService.RespuestaPago r = pagoService.pagar(TURISTA_ID, 15L, datosDelNavegador);

        assertTrue(r.mensaje().startsWith("Venció el plazo"));
        verifyNoInteractions(pasarela);
    }
}
