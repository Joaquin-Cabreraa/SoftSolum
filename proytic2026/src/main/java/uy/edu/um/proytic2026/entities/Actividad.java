package uy.edu.um.proytic2026.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Actividades")
@Getter
@Setter
@NoArgsConstructor
public class Actividad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column (nullable = false)
    private String estado;

    @Column (name = "departamento")
    private String departamento;

    @Column (name = "localidad")

    @Column (name = "duracion_minutos")
    private Integer duracionMinutos;

    @Column (nullable = false)
    private BigDecimal precio;

    @Column (name = "requisitos_edad")
    private String requisitosEdad;

    @Column (name = "min_previos_reservar", nullable = false)
    private Integer minPreviosReservar;

    //Relación "ofrece": Emprendimiento (1) -- (n) Actividdad
    @ManyToOne(optional = false) //el optional false obliga a que toda Actividad tiene que tener un Emprendimiento asociado
    @JoinColumn (name = "emprendimiento_id", nullable = false) //En la actividad se crea una columna "emprendimiento_id"
    private Emprendimiento emprendimiento;

    //Relacion "tiene": Actividad (1) --- (n) Horario
    // mapeado como dueño de esta relación.
    @OneToMany (mappedBy = "actividad", cascade = CascadeType.ALL)
    private List<Horario> horarios = new ArrayList<>();

            /*¿Pq uno tiene mappedBy y el otro no? En el primero (@JoinColumn (name = "emprendimiento_id"...), en actividad se va a crear una columna con las foreign
              keys (pq van en el lado de "muchos" (n)). En el de mappedBy como (viendo el MER) en la relación Actividad-Horario Actividad es el lado de (1), la que va a
              tener la columna con la foreign keys es la tabla Horarios. Por eso es "mapped by", pq está mapeado en la columna de Horarios.
              */

    //relacion "clasificada como": Actividad (n) --- (n) TipoActividad
    // Requiere que exista la entidad TipoActividad.
    @ManyToMany
    @JoinTable (name = "actividad_tipo_actividad", joinColumns = @JoinColumn(name = "actividad_id"), inverseJoinColumns = @JoinColumn(name = "tipo_actividad_id"))
    private List<TipoActividad> tipoActividad = new ArrayList<>();

    //Relación "requiere": Actividad (n) --- (n) TipoEquipamiento
    // Requiere que exista la entidad TipoEquipamiento.
    @ManyToMany
    @JoinTable(
            name = "actividad_tipo_equipamiento",
            joinColumns = @JoinColumn(name = "actividad_id"),
            inverseJoinColumns = @JoinColumn(name = "tipo_equipamiento_id")
    )
    private List<TipoEquipamiento> tiposEquipamiento = new ArrayList<>();
}
