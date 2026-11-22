package uy.edu.um.proytic2026.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.proytic2026.dto.ActividadPublicaDto;
import uy.edu.um.proytic2026.dto.EmprendimientoPublicoDto;
import uy.edu.um.proytic2026.dto.VerificacionDto;
import uy.edu.um.proytic2026.entities.Actividad;
import uy.edu.um.proytic2026.entities.Emprendimiento;
import uy.edu.um.proytic2026.entities.EstadoEmprendimiento;
import uy.edu.um.proytic2026.entities.Horario;
import uy.edu.um.proytic2026.repository.ActividadRepository;
import uy.edu.um.proytic2026.repository.EmprendimientoRepository;
import uy.edu.um.proytic2026.repository.ResenaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Tests unitarios de la API publica (ApiPublicaService):
// listado de emprendimientos y actividades de un dia en un departamento / localidad.
@ExtendWith(MockitoExtension.class)
class ApiPublicaServiceTest {

    private static final LocalDate DIA = LocalDate.of(2026, 12, 1);

    @Mock private EmprendimientoRepository emprendimientoRepository;
    @Mock private ActividadRepository actividadRepository;
    @Mock private CatalogoService catalogoService;
    @Mock private ResenaService resenaService;
    @Mock private ResenaRepository resenaRepository;

    @InjectMocks private ApiPublicaService apiPublicaService;

    // ---------- 1) Listado de emprendimientos ----------

    private Emprendimiento crearEmprendimiento(Long id, String nombre) {
        Emprendimiento e = new Emprendimiento();
        e.setId(id);
        e.setNombre(nombre);
        e.setTelefono("099123456");
        e.setEmail("contacto@kayak.uy");
        e.setUbicacion("Rocha");
        e.setDescripcion("Kayak en laguna");
        e.setEstado(EstadoEmprendimiento.ACEPTADO);
        return e;
    }

    @Test
    void listarEmprendimientos_devuelveSoloLosAceptadosConSusDatosPublicos() {
        when(emprendimientoRepository.findByEstadoOrderByNombreAsc(EstadoEmprendimiento.ACEPTADO))
                .thenReturn(List.of(crearEmprendimiento(1L, "Kayak del Este")));

        List<EmprendimientoPublicoDto> resultado = apiPublicaService.listarEmprendimientos();

        assertEquals(1, resultado.size());
        EmprendimientoPublicoDto dto = resultado.get(0);
        assertEquals(1L, dto.id());
        assertEquals("Kayak del Este", dto.nombre());
        assertEquals("099123456", dto.telefono());
        assertEquals("Rocha", dto.ubicacion());
    }

    @Test
    void listarEmprendimientos_sinEmprendimientosAceptadosDevuelveListaVacia() {
        when(emprendimientoRepository.findByEstadoOrderByNombreAsc(EstadoEmprendimiento.ACEPTADO))
                .thenReturn(List.of());

        assertTrue(apiPublicaService.listarEmprendimientos().isEmpty());
    }

    // ---------- 2) Actividades de un dia en un departamento / localidad ----------

    private Actividad crearActividad(Long id, String titulo, String departamento, String localidad) {
        Emprendimiento emprendimiento = new Emprendimiento();
        emprendimiento.setId(10L);
        emprendimiento.setNombre("Kayak del Este");

        Actividad actividad = new Actividad();
        actividad.setId(id);
        actividad.setTitulo(titulo);
        actividad.setDescripcion("Salida guiada");
        actividad.setDepartamento(departamento);
        actividad.setLocalidad(localidad);
        actividad.setPrecio(new BigDecimal("1500"));
        actividad.setEmprendimiento(emprendimiento);
        return actividad;
    }

    private Horario agregarHorario(Actividad actividad, LocalDate fecha, LocalTime inicio) {
        Horario horario = new Horario();
        horario.setActividad(actividad);
        horario.setFecha(fecha);
        horario.setHoraInicio(inicio);
        horario.setHoraFin(inicio.plusHours(2));
        horario.setCuposDisponibles(8);
        actividad.getHorarios().add(horario);
        return horario;
    }

    @Test
    void devuelveLaActividadConSusHorariosDelDiaOrdenados() {
        Actividad actividad = crearActividad(1L, "Kayak en laguna", "Rocha", "La Paloma");
        agregarHorario(actividad, DIA, LocalTime.of(15, 0));
        agregarHorario(actividad, DIA, LocalTime.of(10, 0));
        agregarHorario(actividad, DIA.plusDays(1), LocalTime.of(9, 0));   // otro dia: no entra
        when(actividadRepository.findAll()).thenReturn(List.of(actividad));
        when(catalogoService.estaPublicada(actividad)).thenReturn(true);

        List<ActividadPublicaDto> resultado = apiPublicaService.actividadesDelDia(DIA, "Rocha", null);

        assertEquals(1, resultado.size());
        ActividadPublicaDto dto = resultado.get(0);
        assertEquals("Kayak en laguna", dto.titulo());
        assertEquals("Kayak del Este", dto.emprendimiento());
        assertEquals(2, dto.horarios().size());
        assertEquals(LocalTime.of(10, 0), dto.horarios().get(0).horaInicio());
        assertEquals(LocalTime.of(15, 0), dto.horarios().get(1).horaInicio());
        assertNull(dto.promedio());
        assertEquals(0L, dto.cantidadValoraciones());
    }

    @Test
    void incluyePromedioRedondeadoYCantidadDeValoraciones() {
        Actividad actividad = crearActividad(1L, "Kayak en laguna", "Rocha", "La Paloma");
        agregarHorario(actividad, DIA, LocalTime.of(10, 0));
        when(actividadRepository.findAll()).thenReturn(List.of(actividad));
        when(catalogoService.estaPublicada(actividad)).thenReturn(true);
        when(resenaService.promedios()).thenReturn(Map.of(1L, new double[]{4.46, 3}));

        ActividadPublicaDto dto = apiPublicaService.actividadesDelDia(DIA, "Rocha", null).get(0);

        assertEquals(4.5, dto.promedio());
        assertEquals(3L, dto.cantidadValoraciones());
    }

    @Test
    void noIncluyeActividadesDeOtroDepartamento() {
        Actividad deRocha = crearActividad(1L, "Kayak en laguna", "Rocha", "La Paloma");
        Actividad deColonia = crearActividad(2L, "Bodega", "Colonia", "Carmelo");
        agregarHorario(deRocha, DIA, LocalTime.of(10, 0));
        agregarHorario(deColonia, DIA, LocalTime.of(11, 0));
        when(actividadRepository.findAll()).thenReturn(List.of(deRocha, deColonia));
        when(catalogoService.estaPublicada(deRocha)).thenReturn(true);
        when(catalogoService.estaPublicada(deColonia)).thenReturn(true);

        List<ActividadPublicaDto> resultado = apiPublicaService.actividadesDelDia(DIA, "Rocha", null);

        assertEquals(1, resultado.size());
        assertEquals(1L, resultado.get(0).id());
    }

    @Test
    void filtraPorLocalidad() {
        Actividad laPaloma = crearActividad(1L, "Kayak en laguna", "Rocha", "La Paloma");
        Actividad cabo = crearActividad(2L, "Senderismo", "Rocha", "Cabo Polonio");
        agregarHorario(laPaloma, DIA, LocalTime.of(10, 0));
        agregarHorario(cabo, DIA, LocalTime.of(11, 0));
        when(actividadRepository.findAll()).thenReturn(List.of(laPaloma, cabo));
        when(catalogoService.estaPublicada(laPaloma)).thenReturn(true);
        when(catalogoService.estaPublicada(cabo)).thenReturn(true);

        List<ActividadPublicaDto> resultado = apiPublicaService.actividadesDelDia(DIA, null, "Cabo Polonio");

        assertEquals(1, resultado.size());
        assertEquals(2L, resultado.get(0).id());
    }

    @Test
    void noDistingueMayusculasNiTildes() {
        Actividad actividad = crearActividad(1L, "Termas", "Paysandú", "Guichón");
        agregarHorario(actividad, DIA, LocalTime.of(10, 0));
        when(actividadRepository.findAll()).thenReturn(List.of(actividad));
        when(catalogoService.estaPublicada(actividad)).thenReturn(true);

        assertEquals(1, apiPublicaService.actividadesDelDia(DIA, "PAYSANDU", null).size());
    }

    @Test
    void noIncluyeActividadesSinHorarioEseDia() {
        Actividad actividad = crearActividad(1L, "Kayak en laguna", "Rocha", "La Paloma");
        agregarHorario(actividad, DIA.plusDays(1), LocalTime.of(10, 0));
        when(actividadRepository.findAll()).thenReturn(List.of(actividad));
        when(catalogoService.estaPublicada(actividad)).thenReturn(true);

        assertTrue(apiPublicaService.actividadesDelDia(DIA, "Rocha", null).isEmpty());
    }

    @Test
    void ignoraLosHorariosCancelados() {
        Actividad actividad = crearActividad(1L, "Kayak en laguna", "Rocha", "La Paloma");
        agregarHorario(actividad, DIA, LocalTime.of(10, 0)).setCancelado(true);
        when(actividadRepository.findAll()).thenReturn(List.of(actividad));
        when(catalogoService.estaPublicada(actividad)).thenReturn(true);

        assertTrue(apiPublicaService.actividadesDelDia(DIA, "Rocha", null).isEmpty());
    }

    @Test
    void noIncluyeActividadesNoPublicadas() {
        Actividad actividad = crearActividad(1L, "Kayak en laguna", "Rocha", "La Paloma");
        agregarHorario(actividad, DIA, LocalTime.of(10, 0));
        when(actividadRepository.findAll()).thenReturn(List.of(actividad));
        when(catalogoService.estaPublicada(actividad)).thenReturn(false);   // emprendimiento sin aceptar o dada de baja

        assertTrue(apiPublicaService.actividadesDelDia(DIA, "Rocha", null).isEmpty());
    }

    @Test
    void sinDepartamentoNiLocalidadRechazaElPedido() {
        assertThrows(IllegalArgumentException.class,
                () -> apiPublicaService.actividadesDelDia(DIA, " ", null));
    }

    @Test
    void sinFechaRechazaElPedido() {
        assertThrows(IllegalArgumentException.class,
                () -> apiPublicaService.actividadesDelDia(null, "Rocha", null));
    }

    // ---------- 3) Top 3 del mes por departamento ----------

    // Fila que devuelve la consulta de promedios: [actividadId, promedio, cantidad]
    private Object[] fila(Long actividadId, double promedio, long cantidad) {
        return new Object[]{actividadId, promedio, cantidad};
    }

    private Actividad publicada(Long id, String departamento) {
        Actividad actividad = crearActividad(id, "Actividad " + id, departamento, "Centro");
        when(catalogoService.estaPublicada(actividad)).thenReturn(true);
        return actividad;
    }

    @Test
    void top3_devuelveLasTresMejoresOrdenadasPorPromedio() {
        Actividad a1 = publicada(1L, "Rocha");
        Actividad a2 = publicada(2L, "Rocha");
        Actividad a3 = publicada(3L, "Rocha");
        Actividad a4 = publicada(4L, "Rocha");
        when(actividadRepository.findAll()).thenReturn(List.of(a1, a2, a3, a4));
        when(resenaRepository.promediosEntre(any(), any())).thenReturn(List.<Object[]>of(
                fila(1L, 4.0, 5), fila(2L, 4.9, 5), fila(3L, 3.5, 5), fila(4L, 4.6, 5)));

        List<ActividadPublicaDto> top = apiPublicaService.top3DelMes("Rocha", YearMonth.of(2026, 11));

        assertEquals(3, top.size());
        assertEquals(2L, top.get(0).id());
        assertEquals(4L, top.get(1).id());
        assertEquals(1L, top.get(2).id());   // la de 3.5 queda afuera
    }

    @Test
    void top3_siEmpatanEnPromedioGanaLaConMasValoraciones() {
        Actividad pocas = publicada(1L, "Rocha");
        Actividad muchas = publicada(2L, "Rocha");
        when(actividadRepository.findAll()).thenReturn(List.of(pocas, muchas));
        when(resenaRepository.promediosEntre(any(), any())).thenReturn(List.<Object[]>of(
                fila(1L, 4.5, 2), fila(2L, 4.5, 10)));

        List<ActividadPublicaDto> top = apiPublicaService.top3DelMes("Rocha", YearMonth.of(2026, 11));

        assertEquals(2L, top.get(0).id());
        assertEquals(1L, top.get(1).id());
    }

    @Test
    void top3_ignoraActividadesDeOtroDepartamento() {
        Actividad deRocha = publicada(1L, "Rocha");
        Actividad deColonia = publicada(2L, "Colonia");
        when(actividadRepository.findAll()).thenReturn(List.of(deRocha, deColonia));
        when(resenaRepository.promediosEntre(any(), any())).thenReturn(List.<Object[]>of(
                fila(1L, 4.0, 3), fila(2L, 5.0, 3)));

        List<ActividadPublicaDto> top = apiPublicaService.top3DelMes("Rocha", YearMonth.of(2026, 11));

        assertEquals(1, top.size());
        assertEquals(1L, top.get(0).id());
    }

    @Test
    void top3_ignoraActividadesSinValoracionesEseMes() {
        Actividad sinValoraciones = publicada(1L, "Rocha");
        when(actividadRepository.findAll()).thenReturn(List.of(sinValoraciones));
        when(resenaRepository.promediosEntre(any(), any())).thenReturn(List.of());

        assertTrue(apiPublicaService.top3DelMes("Rocha", YearMonth.of(2026, 11)).isEmpty());
    }

    @Test
    void top3_ignoraActividadesNoPublicadas() {
        Actividad noPublicada = crearActividad(1L, "Actividad 1", "Rocha", "Centro");
        when(catalogoService.estaPublicada(noPublicada)).thenReturn(false);
        when(actividadRepository.findAll()).thenReturn(List.of(noPublicada));
        when(resenaRepository.promediosEntre(any(), any())).thenReturn(List.<Object[]>of(fila(1L, 5.0, 4)));

        assertTrue(apiPublicaService.top3DelMes("Rocha", YearMonth.of(2026, 11)).isEmpty());
    }

    @Test
    void top3_muestraElPromedioDelMesRedondeadoYNoIncluyeHorarios() {
        Actividad actividad = publicada(1L, "Rocha");
        agregarHorario(actividad, DIA, LocalTime.of(10, 0));
        when(actividadRepository.findAll()).thenReturn(List.of(actividad));
        when(resenaRepository.promediosEntre(any(), any())).thenReturn(List.<Object[]>of(fila(1L, 4.46, 3)));

        ActividadPublicaDto dto = apiPublicaService.top3DelMes("Rocha", YearMonth.of(2026, 11)).get(0);

        assertEquals(4.5, dto.promedio());
        assertEquals(3L, dto.cantidadValoraciones());
        assertTrue(dto.horarios().isEmpty());
    }

    @Test
    void top3_consultaDesdeElPrimerDiaDelMesHastaElPrimerDiaDelSiguiente() {
        apiPublicaService.top3DelMes("Rocha", YearMonth.of(2026, 11));

        verify(resenaRepository).promediosEntre(
                LocalDateTime.of(2026, 11, 1, 0, 0),
                LocalDateTime.of(2026, 12, 1, 0, 0));
    }

    @Test
    void top3_sinDepartamentoRechazaElPedido() {
        assertThrows(IllegalArgumentException.class,
                () -> apiPublicaService.top3DelMes(" ", YearMonth.of(2026, 11)));
    }

    // ---------- 4) Verificar emprendimiento ----------

    @Test
    void verificar_unEmprendimientoAceptadoEstaVerificadoYMuestraSuNombre() {
        when(emprendimientoRepository.findById(1L))
                .thenReturn(Optional.of(crearEmprendimiento(1L, "Kayak del Este")));

        VerificacionDto resultado = apiPublicaService.verificarEmprendimiento(1L);

        assertTrue(resultado.existe());
        assertTrue(resultado.verificado());
        assertEquals("Kayak del Este", resultado.nombre());
    }

    @Test
    void verificar_unEmprendimientoPendienteODenegadoExistePeroNoEstaVerificado() {
        for (EstadoEmprendimiento estado : List.of(EstadoEmprendimiento.PENDIENTE, EstadoEmprendimiento.DENEGADO)) {
            Emprendimiento emprendimiento = crearEmprendimiento(1L, "Kayak del Este");
            emprendimiento.setEstado(estado);
            when(emprendimientoRepository.findById(1L)).thenReturn(Optional.of(emprendimiento));

            VerificacionDto resultado = apiPublicaService.verificarEmprendimiento(1L);

            assertTrue(resultado.existe());
            assertFalse(resultado.verificado());
            assertNull(resultado.nombre());   // no se publica el nombre de lo que no esta aprobado
        }
    }

    @Test
    void verificar_unEmprendimientoInexistenteDevuelveExisteFalse() {
        when(emprendimientoRepository.findById(99L)).thenReturn(Optional.empty());

        VerificacionDto resultado = apiPublicaService.verificarEmprendimiento(99L);

        assertEquals(99L, resultado.id());
        assertFalse(resultado.existe());
        assertFalse(resultado.verificado());
        assertNull(resultado.nombre());
    }

    // ---------- 5) Buscar actividades por palabra clave ----------

    @Test
    void buscar_devuelveLasActividadesEncontradasConSusProximosHorarios() {
        Actividad actividad = crearActividad(1L, "Kayak en laguna", "Rocha", "La Paloma");
        Horario horario = agregarHorario(actividad, DIA, LocalTime.of(10, 0));
        when(catalogoService.buscar("kayak", null, null, null, null)).thenReturn(List.of(actividad));
        when(catalogoService.proximosHorarios(actividad)).thenReturn(List.of(horario));

        List<ActividadPublicaDto> resultado = apiPublicaService.buscarActividades("kayak");

        assertEquals(1, resultado.size());
        assertEquals("Kayak en laguna", resultado.get(0).titulo());
        assertEquals(1, resultado.get(0).horarios().size());
        assertEquals(LocalTime.of(10, 0), resultado.get(0).horarios().get(0).horaInicio());
    }

    @Test
    void buscar_incluyeElPromedioDeValoraciones() {
        Actividad actividad = crearActividad(1L, "Kayak en laguna", "Rocha", "La Paloma");
        when(catalogoService.buscar("kayak", null, null, null, null)).thenReturn(List.of(actividad));
        when(resenaService.promedios()).thenReturn(Map.of(1L, new double[]{4.2, 6}));

        ActividadPublicaDto dto = apiPublicaService.buscarActividades("kayak").get(0);

        assertEquals(4.2, dto.promedio());
        assertEquals(6L, dto.cantidadValoraciones());
    }

    @Test
    void buscar_sinCoincidenciasDevuelveListaVacia() {
        when(catalogoService.buscar("zzz", null, null, null, null)).thenReturn(List.of());

        assertTrue(apiPublicaService.buscarActividades("zzz").isEmpty());
    }

    @Test
    void buscar_sinPalabraClaveRechazaElPedido() {
        assertThrows(IllegalArgumentException.class, () -> apiPublicaService.buscarActividades("  "));
    }
}
