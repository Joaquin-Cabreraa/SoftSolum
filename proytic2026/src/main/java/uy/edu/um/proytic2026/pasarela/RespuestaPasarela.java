package uy.edu.um.proytic2026.pasarela;

// Lo que responde la pasarela. estado: "APROBADO" o "RECHAZADO".
public record RespuestaPasarela(String estado, String codigo, String mensaje) {
}
