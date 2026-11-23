package uy.edu.um.proytic2026.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "verificaciones_emprendimientos")
@Getter
@Setter
@NoArgsConstructor
public class VerificacionEmprendimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "emprendimiento_id", nullable = false)
    private Emprendimiento emprendimiento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "administrador_id", nullable = false)
    private Administrador administrador;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", nullable = false)
    private EstadoEmprendimiento estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false)
    private EstadoEmprendimiento estadoNuevo;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(length = 500)
    private String motivo;
}
