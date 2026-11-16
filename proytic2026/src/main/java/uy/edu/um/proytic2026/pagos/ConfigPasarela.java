package uy.edu.um.proytic2026.pagos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Elige la pasarela al arrancar:
// - si están las variables de entorno MP_ACCESS_TOKEN y MP_PUBLIC_KEY -> Mercado Pago (API real, modo prueba)
// - si no -> pasarela simulada (la app funciona igual, sin cobrar de verdad)
@Configuration
public class ConfigPasarela {

    private static final Logger log = LoggerFactory.getLogger(ConfigPasarela.class);

    @Bean
    public PasarelaPago pasarelaPago(@Value("${mercadopago.access-token:}") String accessToken,
                                     @Value("${mercadopago.public-key:}") String publicKey) {
        if (!accessToken.isBlank() && !publicKey.isBlank()) {
            log.info("Pagos: usando la API de Mercado Pago");
            return new PasarelaMercadoPago(accessToken.trim(), publicKey.trim());
        }
        log.info("Pagos: sin credenciales de Mercado Pago (MP_ACCESS_TOKEN / MP_PUBLIC_KEY), uso la pasarela simulada");
        return new PasarelaSimulada();
    }
}
