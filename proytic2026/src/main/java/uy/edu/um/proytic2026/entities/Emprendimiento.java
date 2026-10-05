package uy.edu.um.proytic2026.entities;
import jakarta.persistence.*; //supuestamente persistance ya hace todo de lo sultimos 3 imports
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;


// emprendimiento asociado a un prestador
@Entity
@Table(name = "Emprendimientos")
@Getter
@Setter
@NoArgsConstructor
public class Emprendimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    private String telefono;

    private String email;

    private String ubicacion;

    private String descripcion;

    @OneToMany(mappedBy = "emprendimiento")
    private List<Actividad> actividades = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoEmprendimiento estado = EstadoEmprendimiento.PENDIENTE;
}