package uy.edu.um.proytic2026.entities;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/*Administrador es una subclase de Usuario. Requere que Usuario tenga @Inheritance(strategy = InheritanceType.JOINED). */
@Entity
@Table(name = "Administradores")
public class Administrador extends Usuario{
}
