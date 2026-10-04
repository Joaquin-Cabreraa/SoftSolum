package uy.edu.um.proytic2026.entities;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Getter;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

@Entity
@Table(name = "usuarios")
@Inheritance(strategy = InheritanceType.JOINED)   // NUEVO
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder                                     // antes era @Builder
@Getter
@Setter

public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre")
    @NotBlank
    @Size(min = 1, max = 50)
    private String name;

    @Column (name = "apellido")
    private String lastName;
    private String userName;

    @NotBlank
    @Email(message = "Formato de correo electrónico no válido")
    @Column(name = "correo", unique = true)
    private String userEmail;

    @Column(name = "telefono")
    private String telefono;

    @Column(name = "contrasena")
    private String passwordHash;


}
