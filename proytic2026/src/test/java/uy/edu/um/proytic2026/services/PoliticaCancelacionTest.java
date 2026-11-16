package uy.edu.um.proytic2026.services;

import org.junit.jupiter.api.Test;
import uy.edu.um.proytic2026.entities.Horario;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

// Texto y plazo de la política de cancelación de un horario (lo que ve el turista)
class PoliticaCancelacionTest {

    private Horario horario(Boolean cancelable, Integer minutos, Integer porcentaje) {
        Horario h = new Horario();
        h.setFecha(LocalDate.of(2026, 12, 5));
        h.setHoraInicio(LocalTime.of(18, 0));
        h.setCancelable(cancelable);
        h.setMinMinutosCancelacion(minutos);
        h.setPorcentajeReembolso(porcentaje);
        return h;
    }

    @Test
    void textosSegunLaPolitica() {
        assertEquals("No admite cancelación", horario(false, null, null).textoPoliticaCancelacion());
        assertEquals("Cancelación gratis hasta 24 h antes (se devuelve el 100 %)", horario(true, 1440, 100).textoPoliticaCancelacion());
        assertEquals("Cancelación hasta 2 días antes, con reembolso del 50 %", horario(true, 2880, 50).textoPoliticaCancelacion());
        assertEquals("Cancelación hasta 3 h antes, sin reembolso", horario(true, 180, 0).textoPoliticaCancelacion());
    }

    @Test
    void elLimiteEsElInicioMenosLaAnticipacion() {
        assertEquals(LocalDateTime.of(2026, 12, 4, 18, 0), horario(true, 1440, 100).limiteCancelacion());
        assertNull(horario(false, null, null).limiteCancelacion());
    }

    // La duración sale del inicio y del fin del horario (la actividad ya no tiene "duración en minutos")
    @Test
    void laDuracionSaleDelInicioYElFin() {
        Horario h = horario(false, null, null);
        h.setHoraFin(LocalTime.of(20, 30));               // empieza 18:00
        assertEquals(150, h.duracionMinutos());
        assertEquals("2 h 30 min", h.textoDuracion());
        assertEquals("45 min", Horario.formatoDuracion(45));
        assertEquals("2 h", Horario.formatoDuracion(120));
    }

    @Test
    void laActividadMuestraElRangoDeDuraciones() {
        uy.edu.um.proytic2026.entities.Actividad actividad = new uy.edu.um.proytic2026.entities.Actividad();
        Horario corto = horario(false, null, null);
        corto.setHoraFin(LocalTime.of(19, 0));
        Horario largo = horario(false, null, null);
        largo.setHoraFin(LocalTime.of(20, 30));
        actividad.getHorarios().add(corto);
        assertEquals("1 h", actividad.textoDuracion());
        actividad.getHorarios().add(largo);
        assertEquals("1 h a 2 h 30 min", actividad.textoDuracion());
    }
}
