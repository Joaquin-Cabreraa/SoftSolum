package uy.edu.um.proytic2026.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.repository.ReservaRepository;
import uy.edu.um.proytic2026.repository.ResenaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Reseñas: solo después de que terminó la actividad, una por reserva; el prestador responde; el admin modera
@ExtendWith(MockitoExtension.class)
class ResenaServiceTest {

    @Mock private ResenaRepository resenaRepository;
    @Mock private ReservaRepository reservaRepository;

    @InjectMocks private ResenaService resenaService;

    private static final Long TURISTA_ID = 1L;
    private Reserva reserva;

    // Reserva paga de una salida que empezó/terminó según "inicio"
    private void reservaConSalida(LocalDateTime inicio) {
        Turista turista = new Turista();
        turista.setId(TURISTA_ID);
        turista.setName("Ana");
        turista.setLastName("Pérez");
        Emprendimiento emp = new Emprendimiento();
        emp.setId(3L);
        Actividad actividad = new Actividad();
        actividad.setEmprendimiento(emp);
        Horario h = new Horario();
        h.setActividad(actividad);
        h.setFecha(inicio.toLocalDate());
        h.setHoraInicio(inicio.toLocalTime());
        h.setHoraFin(inicio.toLocalTime().plusHours(2));
        reserva = new Reserva();
        reserva.setId(9L);
        reserva.setTurista(turista);
        reserva.setHorario(h);
        reserva.setEstado(EstadoReserva.CONFIRMADA);
    }

    @BeforeEach
    void preparar() {
        reservaConSalida(LocalDateTime.now().minusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0));
    }

    @Test
    void noSePuedeResenarAntesDeQueTermine() {
        reservaConSalida(LocalDateTime.now().plusDays(1).withNano(0));
        assertEquals("Vas a poder dejar tu reseña cuando termine la actividad.",
                resenaService.puedeResenar(TURISTA_ID, reserva, LocalDateTime.now()));
    }

    @Test
    void soloReservasPagas() {
        reserva.setEstado(EstadoReserva.CANCELADA);
        assertNotNull(resenaService.puedeResenar(TURISTA_ID, reserva, LocalDateTime.now()));
    }

    @Test
    void unaSolaResenaPorReserva() {
        when(resenaRepository.existsByReservaId(9L)).thenReturn(true);
        assertEquals("Ya dejaste tu reseña para esta actividad.", resenaService.puedeResenar(TURISTA_ID, reserva, LocalDateTime.now()));
    }

    @Test
    void creaLaResenaConPuntajeYComentario() {
        when(reservaRepository.findById(9L)).thenReturn(Optional.of(reserva));
        when(resenaRepository.existsByReservaId(9L)).thenReturn(false);

        assertNull(resenaService.crear(TURISTA_ID, 9L, 5, "  Excelente guía  "));

        ArgumentCaptor<Resena> guardada = ArgumentCaptor.forClass(Resena.class);
        verify(resenaRepository).save(guardada.capture());
        assertEquals(5, guardada.getValue().getPuntaje());
        assertEquals("Excelente guía", guardada.getValue().getComentario());
        assertEquals("Ana P.", guardada.getValue().autor());
    }

    @Test
    void puntajeFueraDeRangoNoSeGuarda() {
        when(reservaRepository.findById(9L)).thenReturn(Optional.of(reserva));
        when(resenaRepository.existsByReservaId(9L)).thenReturn(false);

        assertEquals("Elegí de 1 a 5 estrellas.", resenaService.crear(TURISTA_ID, 9L, 7, null));
        verify(resenaRepository, never()).save(any());
    }

    @Test
    void unPrestadorNoRespondeResenasDeOtroEmprendimiento() {
        Resena resena = new Resena();
        resena.setActividad(reserva.getHorario().getActividad());   // emprendimiento 3
        when(resenaRepository.findById(4L)).thenReturn(Optional.of(resena));

        assertEquals("La reseña no existe.", resenaService.responder(99L, 4L, "Gracias"));
        assertNull(resenaService.responder(3L, 4L, " ¡Gracias por venir! "));
        assertEquals("¡Gracias por venir!", resena.getRespuestaPrestador());
    }

    @Test
    void elAdminOcultaConMotivoYNoSeBorra() {
        Resena resena = new Resena();
        when(resenaRepository.findById(4L)).thenReturn(Optional.of(resena));

        assertNotNull(resenaService.moderar(4L, true, " "));
        assertNull(resenaService.moderar(4L, true, "Lenguaje ofensivo"));
        assertTrue(resena.estaOculta());
        verify(resenaRepository, never()).delete(any());
    }
}
