package uy.edu.um.proytic2026.pasarela;

import java.math.BigDecimal;

// Lo que le mandamos a la pasarela (JSON del POST /pasarela/pagar)
public record SolicitudPasarela(String numeroTarjeta, String vencimiento, String cvv, BigDecimal monto) {

    // Evita que el número y el CVV se impriman por accidente en un log
    @Override
    public String toString() {
        return "SolicitudPasarela[monto=" + monto + "]";
    }
}
