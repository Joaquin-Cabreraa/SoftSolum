package uy.edu.um.proytic2026.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Validación de documentos: cédula uruguaya con dígito verificador o pasaporte
class DocumentosTest {

    @Test
    void cedulaConDigitoCorrecto() {
        assertNull(Documentos.validar("1.234.567-2"));
        assertNull(Documentos.validar("45678905"));
    }

    @Test
    void cedulaConDigitoIncorrecto() {
        assertNotNull(Documentos.validar("1.234.567-3"));
        assertNotNull(Documentos.validar("45678901"));
    }

    @Test
    void pasaporteExtranjero() {
        assertNull(Documentos.validar("AB123456"));
        assertNotNull(Documentos.validar("A1"));            // muy corto
    }

    @Test
    void limpiaPuntosGuionesYMayusculas() {
        assertEquals("12345672", Documentos.limpiar("1.234.567-2"));
        assertEquals("AB123456", Documentos.limpiar("ab 123456"));
    }
}
