package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uy.edu.um.proytic2026.entities.Emprendimiento;
import uy.edu.um.proytic2026.entities.EstadoEmprendimiento;
import uy.edu.um.proytic2026.repository.EmprendimientoRepository;
import java.util.ArrayList;
import java.util.List;
@Service
public class AdministradorService {

    @Autowired
    private EmprendimientoRepository emprendimientoRepository;

    public boolean aceptarEmprendimiento(Long id) {
        Emprendimiento e = emprendimientoRepository.findById(id).orElse(null);

        if (e == null || e.getEstado() != EstadoEmprendimiento.PENDIENTE) {
            return false;
        }

        e.setEstado(EstadoEmprendimiento.ACEPTADO);
        emprendimientoRepository.save(e);
        return true;
    }

    public boolean denegarEmprendimiento(Long id) {
        Emprendimiento e = emprendimientoRepository.findById(id).orElse(null);

        if (e == null || e.getEstado() != EstadoEmprendimiento.PENDIENTE) {
            return false;
        }

        e.setEstado(EstadoEmprendimiento.DENEGADO);
        emprendimientoRepository.save(e);
        return true;
    }
    public List<Emprendimiento> listarPendientes() {

        List<Emprendimiento> pendientes = new ArrayList<>();

        for (Emprendimiento e : emprendimientoRepository.findAll()) {

            if (e.getEstado() == EstadoEmprendimiento.PENDIENTE) {
                pendientes.add(e);
            }
        }

        return pendientes;
    }
}