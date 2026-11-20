package uy.edu.um.proytic2026.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uy.edu.um.proytic2026.entities.Actividad;
import uy.edu.um.proytic2026.entities.Horario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

// Precio con descuento de última hora (lógica en la entidad Horario)
class UltimaHoraTest {

    private Horario horario;
    private final LocalDateTime inicio = LocalDateTime.of(LocalDate.of(2026, 12, 5), LocalTime.of(18, 0));

    @BeforeEach
    void preparar() {
        Actividad actividad = new Actividad();
        actividad.setPrecio(new BigDecimal("1500"));
        actividad.setDescuentoUltimaHora(20);
        actividad.setHorasUltimaHora(3);
        horario = new Horario();
        horario.setActividad(actividad);
        horario.setFecha(inicio.toLocalDate());
        horario.setHoraInicio(inicio.toLocalTime());
    }

    @Test
    void dentroDeLaVentanaTieneDescuento() {
        LocalDateTime dosHorasAntes = inicio.minusHours(2);
        assertTrue(horario.enDescuentoUltimaHora(dosHorasAntes));
        assertEquals(new BigDecimal("1200"), horario.precioPorPersona(dosHorasAntes));
    }

    @Test
    void antesDeLaVentanaPagaElPrecioNormal() {
        LocalDateTime unDiaAntes = inicio.minusDays(1);
        assertFalse(horario.enDescuentoUltimaHora(unDiaAntes));
        assertEquals(new BigDecimal("1500"), horario.precioPorPersona(unDiaAntes));
    }

    @Test
    void sinDescuentoConfiguradoNuncaBaja() {
        horario.getActividad().setDescuentoUltimaHora(null);
        horario.getActividad().setHorasUltimaHora(null);
        assertEquals(new BigDecimal("1500"), horario.precioPorPersona(inicio.minusMinutes(30)));
    }

    @Test
    void validaElDescuentoQueCargaElPrestador() {
        ActividadService servicio = new ActividadService();
        assertNull(servicio.validarUltimaHora(20, 3));
        assertNull(servicio.validarUltimaHora(null, null));   // sin descuento
        assertNotNull(servicio.validarUltimaHora(95, 3));      // demasiado
        assertNotNull(servicio.validarUltimaHora(20, null));   // falta un dato
    }
}
