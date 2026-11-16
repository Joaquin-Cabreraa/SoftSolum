package uy.edu.um.proytic2026.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.proytic2026.entities.Actividad;
import uy.edu.um.proytic2026.repository.ActividadRepository;
import uy.edu.um.proytic2026.repository.EmprendimientoRepository;
import uy.edu.um.proytic2026.repository.PrestadorRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// El administrador quita una actividad del portal: tiene que dar motivo y un correo de contacto
@ExtendWith(MockitoExtension.class)
class AdministradorServiceTest {

    @Mock private EmprendimientoRepository emprendimientoRepository;
    @Mock private PrestadorRepository prestadorRepository;
    @Mock private ActividadRepository actividadRepository;

    @InjectMocks private AdministradorService administradorService;

    private Actividad actividad;

    @BeforeEach
    void preparar() {
        actividad = new Actividad();
        actividad.setId(5L);
        actividad.setEstado("ACTIVA");
        when(actividadRepository.findById(5L)).thenReturn(Optional.of(actividad));
    }

    @Test
    void sinMotivoNoSeQuita() {
        assertEquals("Escribí el motivo: el prestador lo va a ver.",
                administradorService.cambiarEstadoActividad(5L, "BAJA", "  ", "admin@umnatural.uy"));
        assertEquals("ACTIVA", actividad.getEstado());
        verify(actividadRepository, never()).save(any());
    }

    @Test
    void sinCorreoValidoNoSeQuita() {
        assertNotNull(administradorService.cambiarEstadoActividad(5L, "BAJA", "Fotos falsas", "no-es-un-correo"));
        assertEquals("ACTIVA", actividad.getEstado());
    }

    @Test
    void alQuitarlaQuedanElMotivoYElContactoParaElPrestador() {
        assertNull(administradorService.cambiarEstadoActividad(5L, "BAJA", " Fotos falsas ", "Soporte@UMnatural.uy"));
        assertEquals("BAJA", actividad.getEstado());
        assertTrue(actividad.quitadaPorAdministrador());
        assertEquals("Fotos falsas", actividad.getMotivoBajaAdmin());
        assertEquals("soporte@umnatural.uy", actividad.getContactoBajaAdmin());
        assertNotNull(actividad.getFechaBajaAdmin());
    }

    @Test
    void alVolverAPublicarlaSeBorraElMotivo() {
        administradorService.cambiarEstadoActividad(5L, "BAJA", "Fotos falsas", "soporte@umnatural.uy");

        assertNull(administradorService.cambiarEstadoActividad(5L, "ACTIVA"));
        assertEquals("ACTIVA", actividad.getEstado());
        assertFalse(actividad.quitadaPorAdministrador());
        assertNull(actividad.getMotivoBajaAdmin());
    }
}
