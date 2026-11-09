package uy.edu.um.proytic2026.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.proytic2026.entities.Turista;
import uy.edu.um.proytic2026.repository.TuristaRepository;
import uy.edu.um.proytic2026.repository.UsuarioRepository;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Tests unitarios del registro de turista (CU06).
@ExtendWith(MockitoExtension.class)
class TuristaServiceTest {

    @Mock private TuristaRepository turistaRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks private TuristaService turistaService;

    @Test
    void registraConTodosSusDatos() {
        when(usuarioRepository.existsByUserEmailIgnoreCase("Ana@Mail.com")).thenReturn(false);   // la búsqueda ignora mayúsculas
        when(turistaRepository.save(any(Turista.class))).thenAnswer(inv -> inv.getArgument(0));

        Turista turista = turistaService.altaTurista("Ana", "Pérez", "1.234.567-2", "1999-03-02",
                "Ana@Mail.com", "099123456", "clave1234");

        assertNotNull(turista);
        assertEquals("Pérez", turista.getLastName());
        assertEquals("12345672", turista.getDocumento());          // sin puntos ni guion
        assertEquals(LocalDate.of(1999, 3, 2), turista.getFechaNacimiento());
        assertEquals("ana@mail.com", turista.getUserEmail());      // en minúsculas
        assertNotEquals("clave1234", turista.getPasswordHash());   // guardada con BCrypt
    }

    @Test
    void rechazaDatosIncompletosOFechaFutura() {
        assertEquals("Completá todos los campos obligatorios.", turistaService.validarDatos(
                "Ana", "", "12345672", "1999-03-02", "ana@mail.com", "099123456", "clave1234"));
        assertEquals("La fecha de nacimiento no es válida.", turistaService.validarDatos(
                "Ana", "Pérez", "12345672", LocalDate.now().plusDays(1).toString(), "ana@mail.com", "099123456", "clave1234"));
    }

    @Test
    void rechazaCorreoRepetido() {
        when(usuarioRepository.existsByUserEmailIgnoreCase("ana@mail.com")).thenReturn(true);

        assertEquals("Ya existe una cuenta registrada con ese correo electrónico.", turistaService.validarDatos(
                "Ana", "Pérez", "12345672", "1999-03-02", "ana@mail.com", "099123456", "clave1234"));
    }
}
