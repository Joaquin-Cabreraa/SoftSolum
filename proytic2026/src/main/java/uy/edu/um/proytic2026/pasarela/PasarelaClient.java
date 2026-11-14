package uy.edu.um.proytic2026.pasarela;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

// Cliente HTTP de la pasarela. Usa RestClient con TIMEOUT: si la pasarela no contesta a tiempo
// (o está caída) lanza una RestClientException (ResourceAccessException / HttpServerErrorException)
// y PagoService la atrapa y registra el intento como ERROR.
@Component
public class PasarelaClient {

    private final RestClient restClient;

    public PasarelaClient(@Value("${pasarela.url:http://localhost:8080}") String url,
                          @Value("${pasarela.timeout-ms:3000}") long timeoutMs) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofMillis(timeoutMs));
        fabrica.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(url)
                .requestFactory(fabrica)
                .build();
    }

    // Pide el cobro a la pasarela. Puede lanzar RestClientException (timeout, 5xx, sin conexión).
    public RespuestaPasarela cobrar(SolicitudPasarela solicitud) {
        return restClient.post()
                .uri("/pasarela/pagar")
                .body(solicitud)
                .retrieve()
                .body(RespuestaPasarela.class);
    }
}
