package uy.edu.um.proytic2026.services;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uy.edu.um.proytic2026.entities.Emprendimiento;
import uy.edu.um.proytic2026.repository.EmprendimientoRepository;

@Service
public class EmprendimientoService {

    @Autowired
    private EmprendimientoRepository emprendimientoRepository;

    // guarda un emprendimiento en la base de datos
    public Emprendimiento crearEmprendimiento(Emprendimiento emprendimiento) {
        return emprendimientoRepository.save(emprendimiento);
    }
}