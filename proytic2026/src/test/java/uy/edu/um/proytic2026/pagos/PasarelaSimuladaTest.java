package uy.edu.um.proytic2026.pagos;

import org.junit.jupiter.api.Test;
import uy.edu.um.proytic2026.entities.EstadoPago;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

// La pasarela simulada (la que se usa sin credenciales de Mercado Pago) responde según el
// NOMBRE DEL TITULAR, igual que las tarjetas de prueba de Mercado Pago.
class PasarelaSimuladaTest {

    private final PasarelaSimulada pasarela = new PasarelaSimulada();

    private SolicitudPago conTitular(String titular) {
        return new SolicitudPago(new BigDecimal("3000"), "Kayak", "reserva-15", "clave-1",
                null, "visa", null, 1, "ana@mail.com", null, null, titular, "3704");
    }

    @Test
    void aproHaceQueElPagoSeApruebe() {
        ResultadoPago r = pasarela.cobrar(conTitular("APRO"));

        assertEquals(EstadoPago.APROBADO, r.estado());
        assertTrue(r.idExterno().startsWith("SIM-"));
        assertEquals("3704", r.ultimosDigitos());
    }

    @Test
    void fundSeRechazaPorFondosInsuficientes() {
        ResultadoPago r = pasarela.cobrar(conTitular("FUND"));

        assertEquals(EstadoPago.RECHAZADO, r.estado());
        assertEquals("cc_rejected_insufficient_amount", r.detalle());
    }

    @Test
    void losOtrosMotivosDeRechazoSonRechazados() {
        for (String titular : new String[]{"OTHE", "SECU", "EXPI", "FORM", "CALL"}) {
            assertEquals(EstadoPago.RECHAZADO, pasarela.cobrar(conTitular(titular)).estado(), titular);
        }
    }

    @Test
    void contDejaElPagoPendienteDeRevision() {
        assertEquals(EstadoPago.PENDIENTE, pasarela.cobrar(conTitular("CONT")).estado());
    }

    @Test
    void errorSimulaQueLaPasarelaNoResponde() {
        assertThrows(IllegalStateException.class, () -> pasarela.cobrar(conTitular("ERROR")));
    }

    @Test
    void esIndiferenteAMayusculasYEspacios() {
        assertEquals(EstadoPago.RECHAZADO, pasarela.cobrar(conTitular("  fund ")).estado());
    }

    @Test
    void elReembolsoSimuladoSiempreSale() {
        assertTrue(pasarela.reembolsar("SIM-1", new BigDecimal("1500"), "clave"));
        assertEquals("SIMULADA", pasarela.nombre());
        assertNull(pasarela.clavePublica());
    }
}
