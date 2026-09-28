package uy.edu.um.proytic2026.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Prestador es una subclase de Usuario. A su vez es superclase de Empresa y Particular
 * (por herencia disjunta "d" del MER), por eso necesita su propia estrategia JOINED.
 * Requiere que Usuario tenga @Inheritance(strategy = InheritanceType.JOINED).
 */
@Entity
@Table (name = "Prestadores")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor //esto
public class Prestador extends Usuario{

    @Column (name = "descripcion_general")
    private String descripcionGeneral;

    @Column (name = "ubicacion")
    private String ubicacion;

    //Prestador es el lado dueño de la relacion 1 a 1:
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "emprendimiento_id")
    private Emprendimiento emprendimiento; //falta crear emprendimiento
}
