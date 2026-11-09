package uy.edu.um.proytic2026.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.repository.ActividadRepository;
import uy.edu.um.proytic2026.repository.HorarioRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

// Tests unitarios de CatalogoService (CU-T04 Buscar actividades, CU-T05 Detalle).
@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock private ActividadRepository actividadRepository;
    @Mock private HorarioRepository horarioRepository;

    @InjectMocks private CatalogoService catalogoService;

    private Actividad crearActividad(Long id, String titulo, EstadoEmprendimiento estadoEmprendimiento, String estado) {
        Emprendimiento emprendimiento = new Emprendimiento();
        emprendimiento.setNombre("Kayak del Este");
        emprendimiento.setEstado(estadoEmprendimiento);

        Actividad actividad = new Actividad();
        actividad.setId(id);
        actividad.setTitulo(titulo);
        actividad.setEstado(estado);
        actividad.setDepartamento("Rocha");
        actividad.setPrecio(new BigDecimal("1500"));
        actividad.setMinPreviosReservar(60);
        actividad.setEmprendimiento(emprendimiento);
        return actividad;
    }

    private Horario horarioQueEmpiezaEn(Actividad actividad, LocalDateTime inicio) {
        Horario horario = new Horario();
        horario.setActividad(actividad);
        horario.setFecha(inicio.toLocalDate());
        horario.setHoraInicio(inicio.toLocalTime());
        return horario;
    }

    // ---------- Publicación (RNE #8 y #9) ----------

    @Test
    void noPublicaActividadesDeEmprendimientosNoAceptados() {
        assertFalse(catalogoService.estaPublicada(crearActividad(1L, "A", EstadoEmprendimiento.PENDIENTE, "ACTIVA")));
        assertFalse(catalogoService.estaPublicada(crearActividad(2L, "B", EstadoEmprendimiento.DENEGADO, "ACTIVA")));
    }

    @Test
    void noPublicaActividadesDadasDeBaja() {
        assertFalse(catalogoService.estaPublicada(crearActividad(1L, "A", EstadoEmprendimiento.ACEPTADO, "BAJA")));
    }

    @Test
    void publicaActividadesActivasDeEmprendimientosAceptados() {
        assertTrue(catalogoService.estaPublicada(crearActividad(1L, "A", EstadoEmprendimiento.ACEPTADO, "ACTIVA")));
    }

    // ---------- Plazo para reservar ----------

    @Test
    void noSePuedeReservarDentroDeLosMinutosPrevios() {
        Actividad actividad = crearActividad(1L, "A", EstadoEmprendimiento.ACEPTADO, "ACTIVA");   // 60 min previos
        LocalDateTime ahora = LocalDateTime.of(2026, 12, 1, 10, 0);

        // Empieza en 30 minutos: ya se cerró la reserva
        assertFalse(catalogoService.sePuedeReservar(horarioQueEmpiezaEn(actividad, ahora.plusMinutes(30)), ahora));
        // Empieza en 2 horas: todavía se puede
        assertTrue(catalogoService.sePuedeReservar(horarioQueEmpiezaEn(actividad, ahora.plusHours(2)), ahora));
    }

    @Test
    void conCeroMinutosElLimiteEsLaHoraDeInicio() {
        Actividad actividad = crearActividad(1L, "A", EstadoEmprendimiento.ACEPTADO, "ACTIVA");
        actividad.setMinPreviosReservar(0);
        LocalDateTime ahora = LocalDateTime.of(2026, 12, 1, 10, 0);

        assertTrue(catalogoService.sePuedeReservar(horarioQueEmpiezaEn(actividad, ahora.plusMinutes(1)), ahora));
        assertFalse(catalogoService.sePuedeReservar(horarioQueEmpiezaEn(actividad, ahora), ahora));
    }

    // ---------- Búsqueda ----------

    @Test
    void buscaSinImportarMayusculasNiTildesYSoloEntrePublicadas() {
        Actividad laguna = crearActividad(1L, "Kayak en la Laguna de Rocha", EstadoEmprendimiento.ACEPTADO, "ACTIVA");
        Actividad pendiente = crearActividad(2L, "Laguna escondida", EstadoEmprendimiento.PENDIENTE, "ACTIVA");
        Actividad otra = crearActividad(3L, "Cabalgata", EstadoEmprendimiento.ACEPTADO, "ACTIVA");
        otra.getEmprendimiento().setNombre("Estancia");
        when(actividadRepository.findAll()).thenReturn(List.of(laguna, pendiente, otra));

        List<Actividad> resultado = catalogoService.buscar("LÁGUNA", null, null, null, null);

        assertEquals(List.of(laguna), resultado);
    }

    @Test
    void filtraPorPrecioMaximoYTipo() {
        TipoActividad acuatica = new TipoActividad();
        acuatica.setId(5L);
        acuatica.setNombre("Acuática");

        Actividad barata = crearActividad(1L, "Kayak", EstadoEmprendimiento.ACEPTADO, "ACTIVA");
        barata.setPrecio(new BigDecimal("900"));
        barata.getTipoActividad().add(acuatica);
        Actividad cara = crearActividad(2L, "Buceo", EstadoEmprendimiento.ACEPTADO, "ACTIVA");
        cara.setPrecio(new BigDecimal("3000"));
        cara.getTipoActividad().add(acuatica);
        Actividad sinTipo = crearActividad(3L, "Paseo", EstadoEmprendimiento.ACEPTADO, "ACTIVA");
        sinTipo.setPrecio(new BigDecimal("500"));
        when(actividadRepository.findAll()).thenReturn(List.of(barata, cara, sinTipo));

        List<Actividad> resultado = catalogoService.buscar(null, null, 5L, null, new BigDecimal("1000"));

        assertEquals(List.of(barata), resultado);
    }

    @Test
    void proximosHorariosSacaLosSinCuposYLosQueYaCerraron() {
        Actividad actividad = crearActividad(1L, "Kayak", EstadoEmprendimiento.ACEPTADO, "ACTIVA");
        LocalDate manana = LocalDate.now().plusDays(1);

        Horario conCupos = horarioQueEmpiezaEn(actividad, manana.atTime(LocalTime.of(10, 0)));
        conCupos.setCuposDisponibles(4);
        Horario lleno = horarioQueEmpiezaEn(actividad, manana.atTime(LocalTime.of(15, 0)));
        lleno.setCuposDisponibles(0);
        Horario yaEmpezo = horarioQueEmpiezaEn(actividad, LocalDateTime.now().minusMinutes(5));
        yaEmpezo.setCuposDisponibles(8);

        when(horarioRepository.findByActividadIdAndFechaGreaterThanEqualOrderByFechaAscHoraInicioAsc(1L, LocalDate.now()))
                .thenReturn(List.of(yaEmpezo, conCupos, lleno));

        assertEquals(List.of(conCupos), catalogoService.proximosHorarios(actividad));
    }
}
