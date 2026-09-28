package uy.edu.um.proytic2026.entities;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

//Empresa es una subclase de Prestador.
@Entity
@Table(name = "Empresas")
@Getter
@Setter
@NoArgsConstructor
public class Empresa extends Prestador{

    @Column (name = "rut", unique = true, nullable = false)
    private String rut;
}
