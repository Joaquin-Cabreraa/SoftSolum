package uy.edu.um.proytic2026.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import uy.edu.um.proytic2026.entities.TipoActividad;
import uy.edu.um.proytic2026.entities.TipoEquipamiento;
import uy.edu.um.proytic2026.repository.TipoActividadRepository;
import uy.edu.um.proytic2026.repository.TipoEquipamientoRepository;

import java.util.List;

// Al arrancar, si todavía no hay tipos cargados, crea los del enunciado.
// Después el administrador los puede editar o borrar desde su panel.
@Component
public class TiposInicializador implements CommandLineRunner {

    private final TipoActividadRepository tipoActividadRepository;
    private final TipoEquipamientoRepository tipoEquipamientoRepository;

    public TiposInicializador(TipoActividadRepository tipoActividadRepository,
                              TipoEquipamientoRepository tipoEquipamientoRepository) {
        this.tipoActividadRepository = tipoActividadRepository;
        this.tipoEquipamientoRepository = tipoEquipamientoRepository;
    }

    @Override
    public void run(String... args) {
        if (tipoActividadRepository.count() == 0) {
            for (String nombre : List.of("Turismo de naturaleza", "Trekking", "Aventura", "Gastronomía",
                    "Actividad acuática", "Recorrido histórico", "Enoturismo", "Cabalgata", "Senderismo")) {
                TipoActividad tipo = new TipoActividad();
                tipo.setNombre(nombre);
                tipoActividadRepository.save(tipo);
            }
        }
        if (tipoEquipamientoRepository.count() == 0) {
            for (String nombre : List.of("Casco", "Chaleco salvavidas", "Bicicleta", "Equipo de buceo",
                    "Calzado deportivo", "Agua")) {
                TipoEquipamiento tipo = new TipoEquipamiento();
                tipo.setNombre(nombre);
                tipoEquipamientoRepository.save(tipo);
            }
        }
    }
}
