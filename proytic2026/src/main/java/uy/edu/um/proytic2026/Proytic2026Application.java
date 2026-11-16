package uy.edu.um.proytic2026;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Proytic2026Application {

	// Todas las horas de la app (plazos de pago, cierre de reservas, cancelaciones, última hora)
	// se calculan con la hora oficial de Uruguay, sin importar en qué compu o servidor corra.
	// La hora la da el reloj del servidor (sincronizado por internet), nunca el navegador del usuario,
	// así que nadie la puede "adelantar" o "atrasar" para hacer trampa.
	public static final String ZONA_HORARIA = "America/Montevideo";

	public static void main(String[] args) {
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZONA_HORARIA));
		SpringApplication.run(Proytic2026Application.class, args);
	}

}
