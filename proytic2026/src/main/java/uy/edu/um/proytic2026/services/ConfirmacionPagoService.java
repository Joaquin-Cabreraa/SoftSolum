package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uy.edu.um.proytic2026.entities.EstadoReserva;
import uy.edu.um.proytic2026.entities.Reserva;
import uy.edu.um.proytic2026.repository.ReservaRepository;

@Service
public class ConfirmacionPagoService {

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Transactional
    public boolean confirmarReserva(Long reservaId) {

        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow();

        if (reserva.getEstado() != EstadoReserva.PENDIENTE_PAGO) {
            return false;
        }

        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepository.save(reserva);

        eventPublisher.publishEvent(new PagoAprobadoEvent(
                reserva.getTurista().getUserEmail(),
                reserva.getHorario().getActividad().getTitulo(),
                reserva.getHorario().getFecha(),
                reserva.getHorario().getHoraInicio(),
                reserva.getCantCupos(),
                reserva.getMontoTotal()
        ));

        return true;
    }
}
