package uy.edu.um.proytic2026.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

// Turista es una subclase de Usuario (herencia disjunta del MER:
// un usuario es turista, prestador o administrador, nunca dos a la vez).
@Entity
@Table(name = "Turistas")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Turista extends Usuario {

    // Nombre, apellido, correo, teléfono y contraseña los hereda de Usuario
    // (se guardan en la tabla "usuarios"). Acá van los datos propios del turista,
    // que se usan para completar solos sus datos como "Persona 1" al reservar.

    // Documento (CI, o pasaporte si es extranjero)
    @Column(name = "documento", length = 20)
    private String documento;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;
}