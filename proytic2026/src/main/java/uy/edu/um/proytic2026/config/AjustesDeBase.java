package uy.edu.um.proytic2026.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

// Ajuste automático de la base al arrancar (solo PostgreSQL / Neon).
// Hibernate le puso a la columna reservas.estado un CHECK con los estados que existían
// cuando se creó la tabla. ddl-auto=update no lo actualiza, así que el estado nuevo
// EN_ESPERA (lista de espera) daría error. Acá se borra ese CHECK viejo y se vuelve a crear
// con todos los estados. Es seguro correrlo muchas veces.
@Component
public class AjustesDeBase implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AjustesDeBase.class);

    private final DataSource dataSource;

    public AjustesDeBase(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conexion = dataSource.getConnection()) {
            if (!conexion.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres")) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("ALTER TABLE reservas DROP CONSTRAINT IF EXISTS reservas_estado_check");
            jdbc.execute("ALTER TABLE reservas ADD CONSTRAINT reservas_estado_check CHECK "
                    + "(estado IN ('EN_ESPERA','PENDIENTE_PAGO','CONFIRMADA','CANCELADA'))");
        } catch (Exception e) {
            log.warn("No se pudo actualizar el CHECK de reservas.estado: {}", e.getMessage());
        }
    }
}
