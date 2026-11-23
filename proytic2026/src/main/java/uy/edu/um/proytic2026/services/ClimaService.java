package uy.edu.um.proytic2026.services;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class ClimaService {

    private final RestClient cliente = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    // Datos del pronóstico que se muestran al turista.
    public record Pronostico(
            double temperaturaMaxima,
            double temperaturaMinima,
            int probabilidadLluvia,
            String descripcion
    ) {}

    // Consulta el clima para una localidad y fecha de la actividad.
    // Si no hay pronóstico disponible, devuelve Optional.empty().
    public Optional<Pronostico> consultar(
            String localidad, String departamento, LocalDate fecha) {

        String lugarBusqueda = (localidad == null || localidad.isBlank())
                ? departamento
                : localidad;

        if (lugarBusqueda == null || lugarBusqueda.isBlank()
                || fecha == null) {
            return Optional.empty();
        }

        // Open-Meteo ofrece pronósticos de hasta 16 días,
        // contando el día actual.
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), fecha);

        if (dias < 0 || dias > 15) {
            return Optional.empty();
        }

        try {
            // Primero buscamos las coordenadas de la localidad.
            String nombre = URLEncoder.encode(
                    lugarBusqueda, StandardCharsets.UTF_8);

            String urlUbicacion =
                    "https://geocoding-api.open-meteo.com/v1/search"
                            + "?name=" + nombre
                            + "&count=100&language=es&format=json";

            String respuestaUbicacion = cliente.get()
                    .uri(urlUbicacion)
                    .retrieve()
                    .body(String.class);

            JsonNode resultados = mapper.readTree(respuestaUbicacion)
                    .path("results");

            if (!resultados.isArray() || resultados.isEmpty()) {
                return Optional.empty();
            }

            // Buscamos una coincidencia dentro de Uruguay.
            // Si hay varias, priorizamos el departamento indicado.
            JsonNode ubicacion = null;

            for (JsonNode resultado : resultados) {
                if (!"UY".equalsIgnoreCase(
                        resultado.path("country_code").asText())) {
                    continue;
                }

                if (ubicacion == null) {
                    ubicacion = resultado;
                }

                if (departamento != null
                        && departamento.equalsIgnoreCase(
                        resultado.path("admin1").asText())) {
                    ubicacion = resultado;
                    break;
                }
            }

            if (ubicacion == null) {
                return Optional.empty();
            }

            double latitud = ubicacion.path("latitude").asDouble();
            double longitud = ubicacion.path("longitude").asDouble();

            // Con las coordenadas consultamos el pronóstico diario.
            String urlClima =
                    "https://api.open-meteo.com/v1/forecast"
                            + "?latitude=" + latitud
                            + "&longitude=" + longitud
                            + "&daily=temperature_2m_max,temperature_2m_min,"
                            + "precipitation_probability_max,weather_code"
                            + "&timezone=auto"
                            + "&forecast_days=16";

            String respuestaClima = cliente.get()
                    .uri(urlClima)
                    .retrieve()
                    .body(String.class);

            JsonNode diario = mapper.readTree(respuestaClima)
                    .path("daily");

            JsonNode fechas = diario.path("time");

            // La API devuelve varios días; buscamos el de la actividad.
            for (int i = 0; i < fechas.size(); i++) {

                if (fecha.toString().equals(fechas.get(i).asText())) {

                    double maxima = diario.path("temperature_2m_max")
                            .get(i).asDouble();

                    double minima = diario.path("temperature_2m_min")
                            .get(i).asDouble();

                    int lluvia = diario.path("precipitation_probability_max")
                            .get(i).asInt();

                    int codigo = diario.path("weather_code")
                            .get(i).asInt();

                    return Optional.of(new Pronostico(
                            maxima,
                            minima,
                            lluvia,
                            describirClima(codigo)
                    ));
                }
            }

        } catch (Exception e) {
            // Si falla Open-Meteo, la página sigue funcionando
            // y simplemente no muestra el bloque del pronóstico.
            System.err.println(
                    "No se pudo consultar el clima: " + e.getMessage());
        }

        return Optional.empty();
    }

    // Traduce los códigos meteorológicos de Open-Meteo
    // a descripciones fáciles de entender.
    private String describirClima(int codigo) {
        if (codigo == 0) return "Despejado";
        if (codigo <= 3) return "Parcialmente nublado";
        if (codigo <= 48) return "Niebla";
        if (codigo <= 57) return "Llovizna";
        if (codigo <= 67) return "Lluvia";
        if (codigo <= 77) return "Nieve";
        if (codigo <= 82) return "Chubascos";
        if (codigo <= 86) return "Nieve";
        if (codigo >= 95) return "Tormenta";

        return "Condiciones variables";
    }
}
