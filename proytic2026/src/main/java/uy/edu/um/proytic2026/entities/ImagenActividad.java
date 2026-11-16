package uy.edu.um.proytic2026.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// Foto de una actividad. Se guarda en la propia base (columna bytea en PostgreSQL),
// así funciona igual en la compu de cualquiera del equipo sin carpetas compartidas.
// Relación: Actividad (1) --- (n) ImagenActividad
@Entity
@Table(name = "Imagenes_actividad")
@Getter
@Setter
@NoArgsConstructor
public class ImagenActividad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "actividad_id", nullable = false)
    private Actividad actividad;

    // image/jpeg, image/png o image/webp
    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, length = 6 * 1024 * 1024)
    private byte[] datos;
}
