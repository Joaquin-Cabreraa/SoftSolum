package uy.edu.um.proytic2026.services;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uy.edu.um.proytic2026.entities.Emprendimiento;
import uy.edu.um.proytic2026.entities.EstadoEmprendimiento;
import uy.edu.um.proytic2026.repository.EmprendimientoRepository;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.entities.Prestador;
import uy.edu.um.proytic2026.repository.PrestadorRepository;

@Service
public class EmprendimientoService {

    @Autowired
    private EmprendimientoRepository emprendimientoRepository;

    // guarda un emprendimiento en la base de datos
    @Transactional
    public Emprendimiento crearEmprendimiento(
            Emprendimiento emprendimiento, Long prestadorId) {

        Prestador prestador = prestadorRepository.findById(prestadorId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Prestador no encontrado."));

        if (prestador.getEmprendimiento() != null) {
            throw new IllegalStateException(
                    "Este prestador ya tiene un emprendimiento.");
        }

        emprendimiento.setId(null);
        emprendimiento.setEstado(EstadoEmprendimiento.PENDIENTE);
        emprendimiento.setActividades(new java.util.ArrayList<>());

        Emprendimiento nuevo = emprendimientoRepository.save(emprendimiento);

        prestador.setEmprendimiento(nuevo);
        prestadorRepository.save(prestador);

        return nuevo;
    }

    @Autowired
    private PrestadorRepository prestadorRepository;
}