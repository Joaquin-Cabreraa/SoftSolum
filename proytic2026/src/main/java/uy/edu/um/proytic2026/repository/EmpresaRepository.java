package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entities.Empresa;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
    boolean existsByRut(String rut);
    // "¿Existe alguna empresa con este RUT?" → Spring arma la consulta solo a partir del nombre.
}