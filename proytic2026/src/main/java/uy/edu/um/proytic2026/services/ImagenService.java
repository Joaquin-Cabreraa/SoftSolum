package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uy.edu.um.proytic2026.entities.Actividad;
import uy.edu.um.proytic2026.entities.ImagenActividad;
import uy.edu.um.proytic2026.repository.ImagenActividadRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Fotos de las actividades: validar, guardar, borrar y servir.
@Service
public class ImagenService {

    public static final int MAXIMO_POR_ACTIVIDAD = 6;
    public static final long TAMANO_MAXIMO = 5L * 1024 * 1024;   // 5 MB por foto

    @Autowired private ImagenActividadRepository imagenRepository;

    // Solo fotos de verdad: se mira el tipo Y los primeros bytes del archivo
    // (así no se puede subir cualquier cosa cambiándole la extensión).
    public String validar(List<MultipartFile> archivos, int yaTiene) {
        List<MultipartFile> nuevas = conContenido(archivos);
        if (yaTiene + nuevas.size() > MAXIMO_POR_ACTIVIDAD) {
            return "Podés tener hasta " + MAXIMO_POR_ACTIVIDAD + " fotos por actividad.";
        }
        for (MultipartFile archivo : nuevas) {
            if (archivo.getSize() > TAMANO_MAXIMO) {
                return "La foto \"" + archivo.getOriginalFilename() + "\" pesa más de 5 MB.";
            }
            try {
                if (tipoReal(archivo.getBytes()) == null) {
                    return "\"" + archivo.getOriginalFilename() + "\" no es una foto JPG, PNG o WEBP.";
                }
            } catch (IOException e) {
                return "No se pudo leer la foto \"" + archivo.getOriginalFilename() + "\".";
            }
        }
        return null;
    }

    // Guarda las fotos nuevas (ya validadas) y borra las que el prestador marcó para eliminar
    @Transactional
    public void actualizarFotos(Actividad actividad, List<MultipartFile> nuevas, List<Long> eliminar) throws IOException {
        if (eliminar != null) {
            for (Long id : eliminar) {
                imagenRepository.findById(id)
                        .filter(img -> img.getActividad().getId().equals(actividad.getId()))   // solo las suyas
                        .ifPresent(imagenRepository::delete);
            }
        }
        for (MultipartFile archivo : conContenido(nuevas)) {
            byte[] datos = archivo.getBytes();
            ImagenActividad imagen = new ImagenActividad();
            imagen.setActividad(actividad);
            imagen.setDatos(datos);
            imagen.setContentType(tipoReal(datos));
            imagenRepository.save(imagen);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> idsDe(Long actividadId) {
        return actividadId == null ? new ArrayList<>() : imagenRepository.idsDeActividad(actividadId);
    }

    public int cantidad(Long actividadId) {
        return actividadId == null ? 0 : (int) imagenRepository.countByActividadId(actividadId);
    }

    // Para las tarjetas: id de actividad -> id de su primera foto
    @Transactional(readOnly = true)
    public Map<Long, Long> portadas() {
        Map<Long, Long> mapa = new HashMap<>();
        for (Object[] fila : imagenRepository.primeraImagenDeCadaActividad()) {
            mapa.put((Long) fila[0], (Long) fila[1]);
        }
        return mapa;
    }

    @Transactional(readOnly = true)
    public ImagenActividad obtener(Long id) {
        return imagenRepository.findById(id).orElse(null);
    }

    // ---------- Ayuda ----------

    private List<MultipartFile> conContenido(List<MultipartFile> archivos) {
        List<MultipartFile> lista = new ArrayList<>();
        if (archivos != null) {
            for (MultipartFile a : archivos) {
                if (a != null && !a.isEmpty()) lista.add(a);
            }
        }
        return lista;
    }

    // Reconoce el formato por la "firma" del archivo. null si no es JPG/PNG/WEBP.
    static String tipoReal(byte[] b) {
        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }
}
