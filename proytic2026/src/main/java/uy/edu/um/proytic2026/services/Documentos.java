package uy.edu.um.proytic2026.services;

// Reglas para los documentos de las personas (turistas y participantes de una reserva).
// - Si son 7 u 8 números, es una cédula uruguaya: se controla el dígito verificador
//   (así no se cuela una cédula inventada o mal escrita).
// - Si no, se toma como documento extranjero / pasaporte: 5 a 20 letras o números.
public final class Documentos {

    private Documentos() {
    }

    // Saca puntos, guiones y espacios: "1.234.567-2" -> "12345672". En mayúsculas.
    public static String limpiar(String documento) {
        return documento == null ? null : documento.replaceAll("[.\\-\\s]", "").toUpperCase();
    }

    // null si está bien; si no, el motivo
    public static String validar(String documento) {
        String doc = limpiar(documento);
        if (doc == null || doc.isEmpty()) {
            return "Falta el documento.";
        }
        if (doc.matches("[0-9]{7,8}")) {
            return cedulaValida(doc) ? null : "La cédula " + documento.trim() + " no es válida (revisá el dígito verificador).";
        }
        if (!doc.matches("[0-9A-Z]{5,20}")) {
            return "El documento " + documento.trim() + " no es válido.";
        }
        return null;
    }

    // Dígito verificador de la cédula uruguaya (mismo cálculo que en PrestadorService)
    public static boolean cedulaValida(String cedula) {
        if (cedula == null || !cedula.matches("[0-9]{7,8}")) {
            return false;
        }
        String ci = String.format("%8s", cedula).replace(' ', '0');
        int[] multiplicadores = {2, 9, 8, 7, 6, 3, 4};
        int suma = 0;
        for (int i = 0; i < 7; i++) {
            suma += Character.getNumericValue(ci.charAt(i)) * multiplicadores[i];
        }
        int verificador = (10 - (suma % 10)) % 10;
        return verificador == Character.getNumericValue(ci.charAt(7));
    }
}
