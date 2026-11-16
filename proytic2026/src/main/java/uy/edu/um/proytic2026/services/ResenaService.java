package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.repository.ReservaRepository;
import uy.edu.um.proytic2026.repository.ResenaRepository;

import java.time.LocalDateTime;
import java.util.*;

// Reseñas: el turista valora una actividad que ya terminó, el prestador responde
// y el administrador modera (oculta las inapropiadas).
@Service
public class ResenaService {

    @Autowired private ResenaRepository resenaRepository;
    @Autowired private ReservaRepository reservaRepository;

    // ---------- Turista ----------

    // ¿Puede reseñar esta reserva AHORA? null = sí; si no, el motivo.
    // Solo reservas pagas, de una salida que ya TERMINÓ (hora del servidor), y una sola vez.
    public String puedeResenar(Long turistaId, Reserva reserva, LocalDateTime ahora) {
        if (reserva == null || !reserva.getTurista().getId().equals(turistaId)) return "La reserva no existe.";
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA) return "Solo podés reseñar actividades que hiciste.";
        Horario h = reserva.getHorario();
        if (!ahora.isAfter(h.getFecha().atTime(h.getHoraFin()))) {
            return "Vas a poder dejar tu reseña cuando termine la actividad.";
        }
        if (resenaRepository.existsByReservaId(reserva.getId())) return "Ya dejaste tu reseña para esta actividad.";
        return null;
    }

    // Reservas del turista que ya terminaron y todavía no reseñó (para el aviso)
    @Transactional(readOnly = true)
    public List<Reserva> pendientesDeResena(Long turistaId) {
        LocalDateTime ahora = LocalDateTime.now();
        List<Reserva> pendientes = new ArrayList<>();
        for (Reserva r : reservaRepository.findByTuristaIdOrderByFechaCreacionDesc(turistaId)) {
            if (puedeResenar(turistaId, r, ahora) == null) pendientes.add(r);
        }
        return pendientes;
    }

    // Devuelve el error, o null si se guardó
    @Transactional
    public String crear(Long turistaId, Long reservaId, Integer puntaje, String comentario) {
        Reserva reserva = reservaRepository.findById(reservaId).orElse(null);
        String error = puedeResenar(turistaId, reserva, LocalDateTime.now());
        if (error != null) return error;
        if (puntaje == null || puntaje < 1 || puntaje > 5) return "Elegí de 1 a 5 estrellas.";
        if (comentario != null && comentario.length() > 1000) return "El comentario puede tener hasta 1000 caracteres.";

        Resena resena = new Resena();
        resena.setReserva(reserva);
        resena.setActividad(reserva.getHorario().getActividad());
        resena.setTurista(reserva.getTurista());
        resena.setPuntaje(puntaje);
        resena.setComentario(comentario == null || comentario.isBlank() ? null : comentario.trim());
        resena.setFecha(LocalDateTime.now());
        resenaRepository.save(resena);
        return null;
    }

    // Reseña de cada reserva del turista (para "Mis reservas"): id de reserva -> reseña
    @Transactional(readOnly = true)
    public Map<Long, Resena> delTurista(List<Reserva> reservas) {
        Map<Long, Resena> mapa = new HashMap<>();
        for (Reserva r : reservas) {
            resenaRepository.findByReservaId(r.getId()).ifPresent(res -> mapa.put(r.getId(), res));
        }
        return mapa;
    }

    // ---------- Portal ----------

    @Transactional(readOnly = true)
    public List<Resena> visiblesDe(Long actividadId) {
        return resenaRepository.visiblesDeActividad(actividadId);
    }

    // actividadId -> [promedio (double), cantidad (long)]
    @Transactional(readOnly = true)
    public Map<Long, double[]> promedios() {
        Map<Long, double[]> mapa = new HashMap<>();
        for (Object[] fila : resenaRepository.promedios()) {
            mapa.put((Long) fila[0], new double[]{((Number) fila[1]).doubleValue(), ((Number) fila[2]).doubleValue()});
        }
        return mapa;
    }

    // ---------- Prestador ----------

    @Transactional(readOnly = true)
    public List<Resena> delEmprendimiento(Long emprendimientoId) {
        return emprendimientoId == null ? new ArrayList<>() : resenaRepository.delEmprendimiento(emprendimientoId);
    }

    // El prestador responde (o corrige su respuesta). No puede borrar ni cambiar la reseña.
    @Transactional
    public String responder(Long emprendimientoId, Long resenaId, String respuesta) {
        Resena resena = resenaRepository.findById(resenaId).orElse(null);
        if (resena == null || emprendimientoId == null
                || !resena.getActividad().getEmprendimiento().getId().equals(emprendimientoId)) {
            return "La reseña no existe.";
        }
        if (respuesta == null || respuesta.isBlank()) return "Escribí tu respuesta.";
        if (respuesta.length() > 1000) return "La respuesta puede tener hasta 1000 caracteres.";
        resena.setRespuestaPrestador(respuesta.trim());
        resena.setFechaRespuesta(LocalDateTime.now());
        resenaRepository.save(resena);
        return null;
    }

    // ---------- Administrador ----------

    @Transactional(readOnly = true)
    public List<Resena> todas(boolean soloOcultas) {
        List<Resena> lista = new ArrayList<>(resenaRepository.findAllByOrderByFechaDesc());
        if (soloOcultas) lista.removeIf(r -> !r.estaOculta());
        return lista;
    }

    // Ocultar (con motivo) o volver a mostrar. No se borra: queda el registro.
    @Transactional
    public String moderar(Long resenaId, boolean ocultar, String motivo) {
        Resena resena = resenaRepository.findById(resenaId).orElse(null);
        if (resena == null) return "La reseña no existe.";
        if (ocultar && (motivo == null || motivo.isBlank())) return "Escribí por qué se oculta (lo ve el turista).";
        resena.setOculta(ocultar);
        resena.setMotivoOculta(ocultar ? motivo.trim() : null);
        resenaRepository.save(resena);
        return null;
    }

    // Quitar una respuesta inapropiada del prestador
    @Transactional
    public String quitarRespuesta(Long resenaId) {
        Resena resena = resenaRepository.findById(resenaId).orElse(null);
        if (resena == null) return "La reseña no existe.";
        resena.setRespuestaPrestador(null);
        resena.setFechaRespuesta(null);
        resenaRepository.save(resena);
        return null;
    }
}
