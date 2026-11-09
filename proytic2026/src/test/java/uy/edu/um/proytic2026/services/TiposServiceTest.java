package uy.edu.um.proytic2026.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.proytic2026.entities.TipoActividad;
import uy.edu.um.proytic2026.repository.ActividadRepository;
import uy.edu.um.proytic2026.repository.TipoActividadRepository;
import uy.edu.um.proytic2026.repository.TipoEquipamientoRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Tests unitarios del ABM de tipos que hace el administrador (CU-A05).
@ExtendWith(MockitoExtension.class)
class TiposServiceTest {

    @Mock private TipoActividadRepository tipoActividadRepository;
    @Mock private TipoEquipamientoRepository tipoEquipamientoRepository;
    @Mock private ActividadRepository actividadRepository;

    @InjectMocks private TiposService tiposService;

    @Test
    void noSePuedeBorrarUnTipoEnUso() {
        when(tipoActividadRepository.existsById(1L)).thenReturn(true);
        when(actividadRepository.existsByTipoActividad_Id(1L)).thenReturn(true);

        assertEquals("No se puede eliminar: hay actividades que usan este tipo.", tiposService.eliminarTipoActividad(1L));
        verify(tipoActividadRepository, never()).deleteById(any());
    }

    @Test
    void seBorraUnTipoSinUso() {
        when(tipoActividadRepository.existsById(1L)).thenReturn(true);
        when(actividadRepository.existsByTipoActividad_Id(1L)).thenReturn(false);

        assertNull(tiposService.eliminarTipoActividad(1L));
        verify(tipoActividadRepository).deleteById(1L);
    }

    @Test
    void noSeRepitenNombres() {
        when(tipoActividadRepository.existsByNombreIgnoreCase("Kayak")).thenReturn(true);

        assertEquals("Ya existe uno con ese nombre.", tiposService.crearTipoActividad("  Kayak "));
        verify(tipoActividadRepository, never()).save(any());
    }

    @Test
    void creaUnTipoNuevo() {
        when(tipoActividadRepository.existsByNombreIgnoreCase("Kayak")).thenReturn(false);

        assertNull(tiposService.crearTipoActividad("Kayak"));
        verify(tipoActividadRepository).save(any(TipoActividad.class));
    }

    @Test
    void noSeBorraUnEquipamientoEnUso() {
        when(tipoEquipamientoRepository.existsById(2L)).thenReturn(true);
        when(actividadRepository.existsByTiposEquipamiento_Id(2L)).thenReturn(true);

        assertEquals("No se puede eliminar: hay actividades que usan este equipamiento.", tiposService.eliminarTipoEquipamiento(2L));
    }

    @Test
    void elPrestadorPuedeEscribirTiposNuevosYSeReusanLosQueYaExisten() {
        TipoActividad kayak = new TipoActividad();
        kayak.setId(4L);
        kayak.setNombre("Kayak");
        when(tipoActividadRepository.findFirstByNombreIgnoreCase("kayak")).thenReturn(java.util.Optional.of(kayak));
        when(tipoActividadRepository.findFirstByNombreIgnoreCase("Pesca")).thenReturn(java.util.Optional.empty());
        when(tipoActividadRepository.save(any(TipoActividad.class))).thenAnswer(inv -> {
            TipoActividad nuevo = inv.getArgument(0);
            nuevo.setId(9L);
            return nuevo;
        });

        // "kayak" ya existe (se reusa), "Pesca" es nuevo (se crea); los vacíos se ignoran
        assertEquals(java.util.List.of(4L, 9L), tiposService.obtenerOCrearTiposActividad(" kayak , Pesca, ,"));
        verify(tipoActividadRepository, times(1)).save(any(TipoActividad.class));
    }
}
