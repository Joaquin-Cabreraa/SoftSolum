package uy.edu.um.proytic2026.pagos;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import uy.edu.um.proytic2026.entities.EstadoPago;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

// Cobro con la API de Mercado Pago (Checkout API / "Checkout Transparente"):
// el turista nunca sale de UMnatural. El navegador convierte la tarjeta en un token
// con el SDK de Mercado Pago y acá, en el servidor, se crea el pago con ese token:
//   POST https://api.mercadopago.com/v1/payments
// Con credenciales de PRUEBA no se mueve plata real (tarjetas de prueba de Mercado Pago).
public class PasarelaMercadoPago implements PasarelaPago {

    private static final String API = "https://api.mercadopago.com";

    private final String accessToken;   // privado: solo en el servidor (variable de entorno MP_ACCESS_TOKEN)
    private final String publicKey;     // público: lo usa el SDK del navegador (MP_PUBLIC_KEY)
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public PasarelaMercadoPago(String accessToken, String publicKey) {
        this.accessToken = accessToken;
        this.publicKey = publicKey;
    }

    @Override
    public ResultadoPago cobrar(SolicitudPago s) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("transaction_amount", s.monto());
        cuerpo.put("token", s.token());
        cuerpo.put("description", s.descripcion());
        cuerpo.put("installments", s.cuotas() == null ? 1 : s.cuotas());
        cuerpo.put("payment_method_id", s.medioPagoId());
        if (s.emisorId() != null && !s.emisorId().isBlank()) {
            cuerpo.put("issuer_id", s.emisorId().matches("\\d+") ? Long.parseLong(s.emisorId()) : s.emisorId());
        }
        cuerpo.put("external_reference", s.referencia());

        Map<String, Object> pagador = new LinkedHashMap<>();
        pagador.put("email", s.emailPagador());
        if (s.numeroDocumento() != null && !s.numeroDocumento().isBlank()) {
            pagador.put("identification", Map.of("type", s.tipoDocumento() == null ? "CI" : s.tipoDocumento(),
                    "number", s.numeroDocumento()));
        }
        cuerpo.put("payer", pagador);

        JsonNode respuesta = enviar("/v1/payments", cuerpo, s.claveIdempotencia());

        String status = respuesta.path("status").asString("");
        EstadoPago estado = switch (status) {
            case "approved" -> EstadoPago.APROBADO;
            case "in_process", "pending", "authorized" -> EstadoPago.PENDIENTE;
            case "rejected", "cancelled" -> EstadoPago.RECHAZADO;
            default -> EstadoPago.ERROR;
        };
        String detalle = respuesta.path("status_detail").asString(respuesta.path("message").asString(""));
        String id = respuesta.path("id").isMissingNode() ? null : respuesta.path("id").asString();
        return new ResultadoPago(estado, id, detalle,
                respuesta.path("payment_method_id").asString(s.medioPagoId()),
                respuesta.path("card").path("last_four_digits").asString(null));
    }

    @Override
    public boolean reembolsar(String idExterno, BigDecimal monto, String claveIdempotencia) {
        JsonNode r = enviar("/v1/payments/" + idExterno + "/refunds", Map.of("amount", monto), claveIdempotencia);
        return !r.path("id").isMissingNode() && !"rejected".equals(r.path("status").asString(""));
    }

    // POST con el token privado y la clave de idempotencia
    private JsonNode enviar(String ruta, Object cuerpo, String claveIdempotencia) {
        try {
            HttpRequest pedido = HttpRequest.newBuilder(URI.create(API + ruta))
                    .timeout(Duration.ofSeconds(25))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .header("X-Idempotency-Key", claveIdempotencia)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)))
                    .build();
            HttpResponse<String> respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() >= 500) {
                throw new IllegalStateException("Mercado Pago respondió " + respuesta.statusCode());
            }
            // Los 4xx (ej. token vencido, datos mal) vienen con un JSON que explica el problema
            return json.readTree(respuesta.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se cortó la conexión con Mercado Pago", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo conectar con Mercado Pago", e);
        }
    }

    @Override
    public String nombre() {
        return "MERCADO_PAGO";
    }

    @Override
    public String clavePublica() {
        return publicKey;
    }
}
