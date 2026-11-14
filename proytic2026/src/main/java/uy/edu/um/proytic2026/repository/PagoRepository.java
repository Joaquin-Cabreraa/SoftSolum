package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entities.EstadoPago;
import uy.edu.um.proytic2026.entities.Pago;
import uy.edu.um.proytic2026.entities.TipoPago;

import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    // Todos los intentos de una reserva, del más viejo al más nuevo
    List<Pago> findByReservaIdOrderByFechaAscIdAsc(Long reservaId);

    // El cobro aprobado de una reserva (para saber qué devolver en un reembolso)
    Optional<Pago> findFirstByReservaIdAndTipoAndEstado(Long reservaId, TipoPago tipo, EstadoPago estado);

    // ¿Ya se devolvió plata de esta reserva?
    boolean existsByReservaIdAndTipoAndEstado(Long reservaId, TipoPago tipo, EstadoPago estado);
}
