package uy.edu.um.proytic2026.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uy.edu.um.proytic2026.entities.ImagenActividad;
import uy.edu.um.proytic2026.services.ImagenService;

import java.util.concurrent.TimeUnit;

// Sirve las fotos de las actividades: <img src="/imagenes/5">
@Controller
public class ImagenWebController {

    @Autowired
    private ImagenService imagenService;

    @GetMapping("/imagenes/{id}")
    public ResponseEntity<byte[]> imagen(@PathVariable Long id) {
        ImagenActividad imagen = imagenService.obtener(id);
        if (imagen == null) {
            return ResponseEntity.notFound().build();
        }
        // Una foto nunca cambia (si se reemplaza, tiene otro id): el navegador la puede guardar
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imagen.getContentType()))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(imagen.getDatos());
    }
}
