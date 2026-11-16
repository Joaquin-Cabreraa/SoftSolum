package uy.edu.um.proytic2026.services;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Validación de las fotos de las actividades
class ImagenServiceTest {

    private final ImagenService servicio = new ImagenService();

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @Test
    void reconoceLasFotosPorSuContenido() {
        assertEquals("image/jpeg", ImagenService.tipoReal(JPG));
        assertEquals("image/png", ImagenService.tipoReal(PNG));
        assertEquals("image/webp", ImagenService.tipoReal(WEBP));
        assertNull(ImagenService.tipoReal("esto no es una foto".getBytes()));
    }

    @Test
    void rechazaUnArchivoQueNoEsFotoAunqueDigaPng() {
        MultipartFile trucho = new MockMultipartFile("imagenes", "virus.png", "image/png", "MZ ejecutable......".getBytes());
        assertNotNull(servicio.validar(List.of(trucho), 0));
    }

    @Test
    void noDejaPasarDelMaximoDeFotos() {
        List<MultipartFile> fotos = new ArrayList<>();
        for (int i = 0; i < 3; i++) fotos.add(new MockMultipartFile("imagenes", "f" + i + ".jpg", "image/jpeg", JPG));

        assertNull(servicio.validar(fotos, 3));      // 3 + 3 = 6: ok
        assertNotNull(servicio.validar(fotos, 4));   // 4 + 3 = 7: no
    }

    @Test
    void sinFotosNuevasNoHayError() {
        assertNull(servicio.validar(null, 0));
        assertNull(servicio.validar(List.of(new MockMultipartFile("imagenes", new byte[0])), 0));   // input vacío
    }
}
