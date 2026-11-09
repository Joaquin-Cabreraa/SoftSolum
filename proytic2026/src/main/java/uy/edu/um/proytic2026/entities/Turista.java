package uy.edu.um.proytic2026.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// Turista es una subclase de Usuario (herencia disjunta del MER:
// un usuario es turista, prestador o administrador, nunca dos a la vez).
@Entity
@Table(name = "Turistas")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Turista extends Usuario {

    // Por ahora no tiene atributos propios: nombre, apellido, correo,
    // teléfono y contraseña ya los hereda de Usuario.
    // Las reservas y comentarios se agregan cuando existan esas entidades.
}