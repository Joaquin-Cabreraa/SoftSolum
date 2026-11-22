package uy.edu.um.proytic2026.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entities.Emprendimiento;
import uy.edu.um.proytic2026.entities.EstadoEmprendimiento;

import java.util.List;

// permite realizar operaciones en la base de datos con emprendimientos
// Quiero un repositorio para guardar, buscar, modificar y eliminar Emprendimiento en la base de datos, y su ID es Long
public interface EmprendimientoRepository extends JpaRepository<Emprendimiento, Long> {

    // Emprendimientos en cierto estado (la API pública usa ACEPTADO), ordenados por nombre
    List<Emprendimiento> findByEstadoOrderByNombreAsc(EstadoEmprendimiento estado);
}
