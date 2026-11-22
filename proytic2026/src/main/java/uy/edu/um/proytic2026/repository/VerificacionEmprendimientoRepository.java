package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entities.VerificacionEmprendimiento;

import java.util.List;

public interface VerificacionEmprendimientoRepository
        extends JpaRepository<VerificacionEmprendimiento, Long> {

    List<VerificacionEmprendimiento>
    findByEmprendimientoIdOrderByFechaDesc(Long emprendimientoId);

    List<VerificacionEmprendimiento>
    findAllByOrderByFechaDesc();
}
