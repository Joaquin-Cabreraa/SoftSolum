package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uy.edu.um.proytic2026.entities.Emprendimiento;
import uy.edu.um.proytic2026.entities.Empresa;
import uy.edu.um.proytic2026.entities.EstadoEmprendimiento;
import uy.edu.um.proytic2026.entities.Particular;
import uy.edu.um.proytic2026.entities.Prestador;
import uy.edu.um.proytic2026.repository.EmprendimientoRepository;
import uy.edu.um.proytic2026.repository.PrestadorRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdministradorService {

    @Autowired
    private EmprendimientoRepository emprendimientoRepository;

    @Autowired
    private PrestadorRepository prestadorRepository;   // NUEVO

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

    // Devuelve los pendientes con los datos del emprendimiento y de su dueño
    public List<Map<String, Object>> listarPendientes() {

        List<Map<String, Object>> pendientes = new ArrayList<>();

        for (Emprendimiento e : emprendimientoRepository.findAll()) {

            if (e.getEstado() != EstadoEmprendimiento.PENDIENTE) {
                continue;
            }

            Map<String, Object> datos = new HashMap<>();

            // Datos del emprendimiento
            datos.put("id", e.getId());
            datos.put("nombre", e.getNombre());
            datos.put("ubicacion", e.getUbicacion());
            datos.put("descripcion", e.getDescripcion());

            // Datos del dueño (empresa o particular)
            Prestador p = prestadorRepository.findByEmprendimiento(e).orElse(null);

            if (p != null) {
                datos.put("email", p.getUserEmail());
                datos.put("telefono", p.getTelefono());

                if (p instanceof Empresa empresa) {
                    datos.put("tipo", "Empresa");
                    datos.put("razonSocial", empresa.getName());
                    datos.put("rut", empresa.getRut());
                } else if (p instanceof Particular particular) {
                    datos.put("tipo", "Particular");
                    datos.put("responsable", particular.getName() + " " + particular.getLastName());
                    datos.put("documento", particular.getDocumento());
                }
            }

            pendientes.add(datos);
        }

        return pendientes;
    }
}