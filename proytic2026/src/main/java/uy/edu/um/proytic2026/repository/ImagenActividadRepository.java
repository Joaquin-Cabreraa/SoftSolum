package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.proytic2026.entities.ImagenActividad;

import java.util.List;

public interface ImagenActividadRepository extends JpaRepository<ImagenActividad, Long> {

    // Solo los ids (sin traer los bytes) de las fotos de una actividad, en el orden en que se subieron
    @Query("SELECT i.id FROM ImagenActividad i WHERE i.actividad.id = :actividadId ORDER BY i.id")
    List<Long> idsDeActividad(@Param("actividadId") Long actividadId);

    // La primera foto de cada actividad: filas [actividadId, imagenId] (para las tarjetas del buscador)
    @Query("SELECT i.actividad.id, MIN(i.id) FROM ImagenActividad i GROUP BY i.actividad.id")
    List<Object[]> primeraImagenDeCadaActividad();

    long countByActividadId(Long actividadId);
}
