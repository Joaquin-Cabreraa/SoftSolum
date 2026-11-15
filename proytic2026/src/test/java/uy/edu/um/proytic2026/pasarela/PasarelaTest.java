package uy.edu.um.proytic2026.pasarela;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.repository.PagoRepository;
import uy.edu.um.proytic2026.repository.ReservaRepository;
import uy.edu.um.proytic2026.services.PagoService;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Prueba la pasarela simulada y el TIMEOUT real del cliente HTTP, contra un servidor local lento
// (no usa internet ni la base de datos).
class PasarelaTest {

    private HttpServer servidorLento;

    @BeforeEach
    void levantarServidorLento() throws Exception {
        servidorLento = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidorLento.createContext("/pasarela/pagar", exchange -> {
            try {
                Thread.sleep(1500);                              // mucho más que el timeout del cliente
            } catch (InterruptedException ignorada) {
                Thread.currentThread().interrupt();
            }
            byte[] cuerpo = "{\"estado\":\"APROBADO\",\"codigo\":\"X\",\"mensaje\":\"tarde\"}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(200, cuerpo.length);
                exchange.getResponseBody().write(cuerpo);
            } catch (java.io.IOException clienteYaSeFue) {
                // el cliente cortó por timeout: es lo esperado
            } finally {
                exchange.close();
            }
        });
        servidorLento.start();
    }

    @AfterEach
    void apagar() {
        servidorLento.stop(0);
    }

    private PasarelaClient clienteHaciaServidorLento() {
        return new PasarelaClient("http://localhost:" + servidorLento.getAddress().getPort(), 300);
    }

    @Test
    void elClienteCortaPorTimeoutSiLaPasarelaTarda() {
        long inicio = System.currentTimeMillis();

        assertThrows(RestClientException.class, () ->
                clienteHaciaServidorLento().cobrar(new SolicitudPasarela("4111111111111111", "12/30", "123", BigDecimal.TEN)));

        assertTrue(System.currentTimeMillis() - inicio < 1400, "tendría que haber cortado a los ~300 ms");
    }

    @Test
    void conTimeoutRealElPagoQuedaEnErrorYLaReservaPendiente() {
        PagoRepository pagoRepository = mock(PagoRepository.class);
        ReservaRepository reservaRepository = mock(ReservaRepository.class);

        Turista turista = new Turista();
        turista.setId(1L);
        Reserva reserva = new Reserva();
        reserva.setId(5L);
        reserva.setTurista(turista);
        reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
        reserva.setFechaCreacion(LocalDateTime.now());
        reserva.setMontoTotal(new BigDecimal("3000"));
        when(reservaRepository.findByIdParaPagar(5L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        PagoService servicio = new PagoService();
        ReflectionTestUtils.setField(servicio, "pagoRepository", pagoRepository);
        ReflectionTestUtils.setField(servicio, "reservaRepository", reservaRepository);
        ReflectionTestUtils.setField(servicio, "pasarelaClient", clienteHaciaServidorLento());

        String vence = String.format("%02d/%02d", 12, (YearMonth.now().getYear() + 2) % 100);
        Pago pago = assertDoesNotThrow(() -> servicio.pagar(1L, 5L, "4000000000003333", vence, "123"));

        assertEquals(EstadoPago.ERROR, pago.getEstado());
        assertEquals(EstadoReserva.PENDIENTE_PAGO, reserva.getEstado());
    }

    @Test
    void laPasarelaSimuladaRespondeSegunElFinalDeLaTarjeta() {
        PasarelaSimuladaController pasarela = new PasarelaSimuladaController();
        ReflectionTestUtils.setField(pasarela, "demoraMs", 50L);

        var aprobada = pasarela.pagar(new SolicitudPasarela("4111 1111 1111 1111", "12/30", "123", BigDecimal.TEN));
        assertEquals("APROBADO", aprobada.getBody().estado());
        assertNotNull(aprobada.getBody().codigo());

        var rechazada = pasarela.pagar(new SolicitudPasarela("4000000000002222", "12/30", "123", BigDecimal.TEN));
        assertEquals("RECHAZADO", rechazada.getBody().estado());
        assertEquals("Fondos insuficientes", rechazada.getBody().mensaje());

        var caida = pasarela.pagar(new SolicitudPasarela("4000000000004444", "12/30", "123", BigDecimal.TEN));
        assertEquals(503, caida.getStatusCode().value());

        var otra = pasarela.pagar(new SolicitudPasarela("4000000000005555", "12/30", "123", BigDecimal.TEN));
        assertEquals("RECHAZADO", otra.getBody().estado());
    }

    @Test
    void laTarjetaTerminadaEn3333TardaMasQueElTimeout() {
        PasarelaSimuladaController pasarela = new PasarelaSimuladaController();
        ReflectionTestUtils.setField(pasarela, "demoraMs", 400L);

        long inicio = System.currentTimeMillis();
        pasarela.pagar(new SolicitudPasarela("4000000000003333", "12/30", "123", BigDecimal.TEN));

        assertTrue(System.currentTimeMillis() - inicio >= 400);
    }
}
