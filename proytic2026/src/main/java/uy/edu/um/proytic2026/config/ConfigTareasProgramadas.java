package uy.edu.um.proytic2026.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Activa las tareas automáticas (@Scheduled), por ejemplo
// ReservaService.cancelarReservasVencidas, que corre cada 5 minutos.
@Configuration
@EnableScheduling
public class ConfigTareasProgramadas {
}
