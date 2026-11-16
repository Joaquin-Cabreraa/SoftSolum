package uy.edu.um.proytic2026.pagos;

import java.math.BigDecimal;

// La app habla con "una pasarela de pagos", sin saber cuál es.
// Hoy hay dos: PasarelaMercadoPago (la API real, en modo prueba) y PasarelaSimulada
// (para correr la app sin credenciales). Cambiar de pasarela = escribir otra clase
// que implemente esta interfaz; el resto del código no se toca.
public interface PasarelaPago {

    // Intenta cobrar. Si no se puede hablar con la pasarela, tira una excepción.
    ResultadoPago cobrar(SolicitudPago solicitud);

    // Devuelve plata de un pago aprobado. true si salió bien.
    boolean reembolsar(String idExterno, BigDecimal monto, String claveIdempotencia);

    // "MERCADO_PAGO" o "SIMULADA" (la página de pago cambia según cuál sea)
    String nombre();

    // Clave pública para el SDK del navegador (null en la simulada)
    String clavePublica();
}
