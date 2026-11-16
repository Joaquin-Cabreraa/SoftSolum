package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entities.EstadoPago;
import uy.edu.um.proytic2026.entities.Pago;

import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    List<Pago> findByReservaIdOrderByFechaDesc(Long reservaId);

    long countByReservaId(Long reservaId);

    Optional<Pago> findFirstByReservaIdAndEstado(Long reservaId, EstadoPago estado);
}
