package uy.edu.um.proytic2026.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "Horarios")
@Getter
@Setter
@NoArgsConstructor
@IdClass(HorarioId.class)
public class Horario {

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "actividad_id", nullable = false)
    private Actividad actividad;

    @Id
    @Column(nullable = false)
    private LocalDate fecha;

    @Id
    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "cupos_totales", nullable = false)
    private Integer cuposTotales;

    @Column(name = "cupos_disponibles", nullable = false)
    private Integer cuposDisponibles;

    @Column(nullable = false)
    private Boolean cancelable;

    @Column(name = "porcentaje_reembolso")
    private Integer porcentajeReembolso;

    @Column(name = "min_minutos_cancelacion")
    private Integer minMinutosCancelacion;

    // ---------- Salida cancelada por el prestador ----------
    // Si el prestador cancela la salida (mal tiempo, imprevisto...), se le devuelve el 100 %
    // a todos los que pagaron. Solo puede hacerlo hasta HORAS_ANTES_CANCELAR_SALIDA antes.
    public static final int HORAS_ANTES_CANCELAR_SALIDA = 24;

    @Column(name = "cancelado")
    private Boolean cancelado;

    @Column(name = "motivo_cancelacion", length = 300)
    private String motivoCancelacion;

    @Column(name = "fecha_cancelacion")
    private LocalDateTime fechaCancelacion;

    public boolean estaCancelado() {
        return Boolean.TRUE.equals(cancelado);
    }

    // Horario "eliminado" por el prestador que tenía reservas ya canceladas: no se borra de la base
    // (las reservas canceladas y sus pagos lo siguen referenciando), solo se oculta de todos lados.
    @Column(name = "eliminado")
    private Boolean eliminado;

    public boolean estaEliminado() {
        return Boolean.TRUE.equals(eliminado);
    }

    // ¿Se puede ver y reservar? (ni cancelado ni eliminado)
    public boolean disponible() {
        return !estaCancelado() && !estaEliminado();
    }

    // Hasta cuándo el prestador puede cancelar la salida
    public LocalDateTime limiteCancelacionPrestador() {
        return fecha.atTime(horaInicio).minusHours(HORAS_ANTES_CANCELAR_SALIDA);
    }

    // ---------- Descuento de última hora ----------

    // ¿Este horario está en la ventana de "última hora"? (entre X horas antes del inicio y el inicio)
    public boolean enDescuentoUltimaHora(LocalDateTime ahora) {
        if (actividad == null || !actividad.tieneDescuentoUltimaHora()) return false;
        LocalDateTime inicio = fecha.atTime(horaInicio);
        LocalDateTime desde = inicio.minusHours(actividad.getHorasUltimaHora());
        return !ahora.isBefore(desde) && ahora.isBefore(inicio);
    }

    // Precio por persona en este momento (con el descuento si corresponde)
    public BigDecimal precioPorPersona(LocalDateTime ahora) {
        BigDecimal precio = actividad.getPrecio();
        if (!enDescuentoUltimaHora(ahora)) return precio;
        BigDecimal factor = BigDecimal.valueOf(100 - actividad.getDescuentoUltimaHora())
                .divide(BigDecimal.valueOf(100));
        return precio.multiply(factor).setScale(0, RoundingMode.HALF_UP);
    }

    // Versiones "ahora" para usar desde las páginas
    public boolean enDescuentoUltimaHora() { return enDescuentoUltimaHora(LocalDateTime.now()); }
    public BigDecimal precioActual() { return precioPorPersona(LocalDateTime.now()); }

    // ---------- Política de cancelación (la define el prestador en cada horario) ----------
    // cancelable = false                     -> no se puede cancelar una reserva paga
    // cancelable = true, porcentaje = 0      -> se puede cancelar hasta X antes, sin reembolso
    // cancelable = true, porcentaje > 0      -> se puede cancelar hasta X antes y se devuelve ese %

    public LocalDateTime inicio() {
        return fecha.atTime(horaInicio);
    }

    // Último momento para cancelar (null si no admite cancelación)
    public LocalDateTime limiteCancelacion() {
        if (!Boolean.TRUE.equals(cancelable)) return null;
        int minutos = minMinutosCancelacion == null ? 0 : minMinutosCancelacion;
        return inicio().minusMinutes(minutos);
    }

    public int reembolso() {
        return Boolean.TRUE.equals(cancelable) && porcentajeReembolso != null ? porcentajeReembolso : 0;
    }

    // Texto para mostrarle al turista, ej. "Cancelación con reembolso del 50 % hasta 24 h antes"
    public String textoPoliticaCancelacion() {
        if (!Boolean.TRUE.equals(cancelable)) return "No admite cancelación";
        String hasta = "hasta " + anticipacion(minMinutosCancelacion == null ? 0 : minMinutosCancelacion);
        int r = reembolso();
        if (r >= 100) return "Cancelación gratis " + hasta + " (se devuelve el 100 %)";
        if (r > 0) return "Cancelación " + hasta + ", con reembolso del " + r + " %";
        return "Cancelación " + hasta + ", sin reembolso";
    }

    // 1440 -> "24 h antes", 2880 -> "2 días antes", 30 -> "30 min antes", 0 -> "el inicio"
    static String anticipacion(int minutos) {
        if (minutos <= 0) return "el inicio";
        if (minutos % 1440 == 0 && minutos >= 2880) return (minutos / 1440) + " días antes";
        if (minutos % 60 == 0) return (minutos / 60) + " h antes";
        if (minutos > 60) return (minutos / 60) + " h " + (minutos % 60) + " min antes";
        return minutos + " min antes";
    }

    // ---------- Duración (sale del inicio y del fin) ----------

    public long duracionMinutos() {
        return java.time.Duration.between(horaInicio, horaFin).toMinutes();
    }

    public String textoDuracion() {
        return formatoDuracion(duracionMinutos());
    }

    // 150 -> "2 h 30 min", 120 -> "2 h", 45 -> "45 min"
    public static String formatoDuracion(long minutos) {
        long h = minutos / 60, m = minutos % 60;
        if (h == 0) return m + " min";
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }
}
