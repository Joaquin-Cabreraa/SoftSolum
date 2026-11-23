package uy.edu.um.proytic2026.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import uy.edu.um.proytic2026.entities.Turista;
import uy.edu.um.proytic2026.repository.EmprendimientoRepository;
import uy.edu.um.proytic2026.repository.TuristaRepository;
import uy.edu.um.proytic2026.repository.UsuarioRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// "Mis datos": modificar datos y contraseña
@ExtendWith(MockitoExtension.class)
class PerfilServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EmprendimientoRepository emprendimientoRepository;
    @Mock private TuristaRepository turistaRepository;

    @InjectMocks private PerfilService perfilService;

    private Turista ana;

    @BeforeEach
    void preparar() {
        ana = new Turista();
        ana.setId(1L);
        ana.setName("Ana");
        ana.setUserEmail("ana@mail.com");
        ana.setPasswordHash(new BCryptPasswordEncoder().encode("clave1234"));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(ana));
    }

    @Test
    void elTuristaModificaSusDatos() {
        String error = perfilService.actualizarTurista(1L, "Ana María", "Pérez", "1.234.567-2",
                "1999-03-02", "ANA.NUEVA@mail.com", "099 123 456");

        assertNull(error);
        assertEquals("Ana María", ana.getName());
        assertEquals("12345672", ana.getDocumento());
        assertEquals("ana.nueva@mail.com", ana.getUserEmail());   // en minúscula
        assertEquals("099123456", ana.getTelefono());
        verify(usuarioRepository).save(ana);
    }

    @Test
    void noPuedeUsarElCorreoDeOtraCuenta() {
        when(usuarioRepository.existsByUserEmailIgnoreCase("beto@mail.com")).thenReturn(true);

        String error = perfilService.actualizarTurista(1L, "Ana", "Pérez", "12345672",
                "1999-03-02", "beto@mail.com", "099123456");

        assertEquals("Ya existe otra cuenta con ese correo electrónico.", error);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void noPuedeUsarElDocumentoDeOtraCuenta() {
        when(turistaRepository.existsByDocumentoIgnoreCaseAndIdNot("12345672", 1L)).thenReturn(true);

        String error = perfilService.actualizarTurista(1L, "Ana", "Pérez", "12345672",
                "1999-03-02", "ana@mail.com", "099123456");

        assertEquals("Ya existe otra cuenta registrada con ese documento.", error);
    }

    @Test
    void cambiaLaContrasenaSoloConLaActualCorrecta() {
        assertEquals(
                "La contraseña actual no es correcta.",
                perfilService.cambiarContrasena(
                        1L, "otra", "contrasenaNueva12345", "contrasenaNueva12345"
                )
        );

        assertNull(
                perfilService.cambiarContrasena(
                        1L, "clave1234", "contrasenaNueva12345", "contrasenaNueva12345"
                )
        );

        assertTrue(
                new BCryptPasswordEncoder().matches(
                        "contrasenaNueva12345", ana.getPasswordHash()
                )
        );
    }
}
