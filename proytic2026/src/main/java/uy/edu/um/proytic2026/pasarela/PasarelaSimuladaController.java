package uy.edu.um.proytic2026.pasarela;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// PASARELA DE PAGOS SIMULADA. Hace de "servicio externo" (tipo MercadoPago/Stripe):
// vive en su propio controller, fuera de /panel, y la aplicación le habla solo por HTTP
// (ver PasarelaClient), igual que lo haría con una pasarela real.
//
// Según cómo termina el número de tarjeta responde:
//   ...1111 -> APROBADO
//   ...2222 -> RECHAZADO (fondos insuficientes)
//   ...3333 -> tarda más que el timeout (aprueba, pero tarde: la aplicación ya no espera)
//   ...4444 -> HTTP 503 (pasarela caída)
//   cualquier otra -> RECHAZADO (tarjeta no reconocida)
@RestController
@RequestMapping("/pasarela")
public class PasarelaSimuladaController {

    // Cuánto tarda la tarjeta ...3333. Tiene que ser MAYOR que pasarela.timeout-ms.
    @Value("${pasarela.simulada.demora-ms:6000}")
    private long demoraMs;

    @PostMapping("/pagar")
    public ResponseEntity<RespuestaPasarela> pagar(@RequestBody SolicitudPasarela solicitud) {
        String tarjeta = solicitud.numeroTarjeta() == null ? "" : solicitud.numeroTarjeta().replaceAll("\\D", "");

        if (tarjeta.endsWith("1111")) {
            return ResponseEntity.ok(new RespuestaPasarela("APROBADO", nuevoCodigo(), "Pago aprobado"));
        }
        if (tarjeta.endsWith("2222")) {
            return ResponseEntity.ok(new RespuestaPasarela("RECHAZADO", nuevoCodigo(), "Fondos insuficientes"));
        }
        if (tarjeta.endsWith("3333")) {
            dormir(demoraMs);
            return ResponseEntity.ok(new RespuestaPasarela("APROBADO", nuevoCodigo(), "Pago aprobado (tarde)"));
        }
        if (tarjeta.endsWith("4444")) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new RespuestaPasarela("ERROR", null, "Servicio no disponible"));
        }
        return ResponseEntity.ok(new RespuestaPasarela("RECHAZADO", nuevoCodigo(), "Tarjeta no reconocida"));
    }

    private String nuevoCodigo() {
        return "PSR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
