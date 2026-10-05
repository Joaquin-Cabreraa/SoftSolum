package uy.edu.um.proytic2026.entities;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

//Particular es subclase de Prestador.
@Entity
@Table(name = "Particulares")
@Getter
@Setter
@NoArgsConstructor
public class Particular extends Prestador{

    @Column(name = "documento", unique = true, nullable = false)
    private String documento;
}
