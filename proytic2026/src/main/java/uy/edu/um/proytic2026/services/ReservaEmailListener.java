
package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import uy.edu.um.proytic2026.services.ReservaCanceladaEvent;

import java.time.format.DateTimeFormatter;

@Component
public class ReservaEmailListener {

    // El servicio es opcional para que la aplicación funcione
    // incluso cuando el envío de correos está deshabilitado.
    @Autowired(required = false)
    private EmailService emailService;

    @Value("${umnatural.mail.enabled:false}")
    private boolean correoHabilitado;

    // Los correos se envían después de confirmar la transacción.
    // Así evitamos notificar operaciones que no llegaron a guardarse.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviarConfirmacion(ReservaCreadaEvent evento) {

        if (!correoHabilitado || emailService == null) {
            return;
        }

        // Avisa que la reserva fue registrada y está pendiente de pago.
        String asunto = "Recibimos tu reserva - UMnatural";

        String mensaje = """
                ¡Hola!

                Tu reserva fue registrada correctamente.

                Actividad: %s
                Fecha: %s
                Hora: %s
                Participantes: %d
                Importe total: $%s
                Estado: Pendiente de pago

                Tenés 2 horas para completar el pago.

                ¡Gracias por elegir UMnatural!
                """.formatted(
                evento.nombreActividad(),
                evento.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                evento.hora().format(DateTimeFormatter.ofPattern("HH:mm")),
                evento.cantidad(),
                evento.monto()
        );

        try {
            emailService.enviarCorreo(
                    evento.emailTurista(),
                    asunto,
                    mensaje
            );
        } catch (Exception e) {
            // Un error de correo no debe interrumpir el proceso de reserva.
            System.err.println(
                    "No se pudo enviar el correo de reserva: " + e.getMessage()
            );
        }
    }

    // Notifica al turista cuando se aprueba el pago.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviarConfirmacionPago(PagoAprobadoEvent evento) {

        if (!correoHabilitado || emailService == null) {
            return;
        }

        String asunto = "¡Pago confirmado! - UMnatural";

        String mensaje = """
            ¡Hola!

            Tu pago fue aprobado y tu reserva está confirmada.

            Actividad: %s
            Fecha: %s
            Hora: %s
            Participantes: %d
            Importe abonado: $%s

            Estado: CONFIRMADA Y PAGA

            ¡Gracias por elegir UMnatural!
            ¡Te esperamos!
            """.formatted(
                evento.nombreActividad(),
                evento.fecha().format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")
                ),
                evento.hora().format(
                        DateTimeFormatter.ofPattern("HH:mm")
                ),
                evento.cantidad(),
                evento.monto()
        );

        try {
            emailService.enviarCorreo(
                    evento.emailTurista(),
                    asunto,
                    mensaje
            );
        } catch (Exception e) {
            System.err.println(
                    "Error enviando correo de pago: " + e.getMessage()
            );
        }
    }

    // Informa la cancelación y si corresponde devolver el dinero.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviarCancelacion(ReservaCanceladaEvent evento) {

        if (!correoHabilitado || emailService == null) {
            return;
        }

        String asunto = evento.canceladaPorPrestador()
                ? "Tu actividad fue cancelada - UMnatural"
                : "Cancelación de reserva - UMnatural";

        String motivo = evento.canceladaPorPrestador()
                ? "El prestador canceló la actividad."
                : "Tu reserva fue cancelada correctamente.";

        String detalleMotivo = "";

        // Si canceló el prestador, incluimos el motivo que ingresó.
        if (evento.canceladaPorPrestador()
                && evento.motivoCancelacion() != null
                && !evento.motivoCancelacion().isBlank()) {

            detalleMotivo = "Motivo de cancelación: "
                    + evento.motivoCancelacion().trim()
                    + "\n";
        }

        // El mensaje cambia según el estado del pago y el reembolso.
        String informacionPago;

        if (!evento.estabaPaga()) {
            informacionPago = "No se realizó ningún cobro, por lo que no corresponde un reembolso.";
        } else if (evento.montoReembolsado() != null
                && evento.montoReembolsado().signum() > 0) {
            informacionPago = "Importe del reembolso: $U "
                    + evento.montoReembolsado() + ".";
        } else {
            informacionPago = "Según las condiciones de cancelación, no corresponde un reembolso.";
        }

        String mensaje = """
        Hola:

        %s
        %s
        Actividad: %s
        Fecha: %s
        Hora: %s

        %s

        Gracias por utilizar UMnatural.
        """.formatted(
                motivo,
                detalleMotivo,
                evento.nombreActividad(),
                evento.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                evento.hora().format(DateTimeFormatter.ofPattern("HH:mm")),
                informacionPago
        );

        try {
            emailService.enviarCorreo(
                    evento.emailTurista(),
                    asunto,
                    mensaje
            );
        } catch (Exception e) {
            System.err.println("Error enviando correo de cancelación: " + e.getMessage());
        }
    }

    // Se ejecuta cuando se libera un lugar y se asigna
    // a un turista que estaba en lista de espera.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviarCupoAsignado(CupoAsignadoEvent evento) {

        if (!correoHabilitado || emailService == null) {
            return;
        }

        String asunto = "¡Conseguiste un cupo! - UMnatural";

        String mensaje = """
            ¡Buenas noticias!

            Se liberó un lugar y te asignamos un cupo
            para la actividad que estabas esperando.

            Actividad: %s
            Fecha: %s
            Hora: %s
            Participantes: %d
            Importe a pagar: $U %s

            Tenés 2 horas para completar el pago
            y confirmar tu reserva.

            Entrá a UMnatural, ingresá a "Mis reservas"
            y seleccioná "Pagar".

            ¡Gracias por elegir UMnatural!
            """.formatted(
                evento.nombreActividad(),
                evento.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                evento.hora().format(DateTimeFormatter.ofPattern("HH:mm")),
                evento.cantidad(),
                evento.monto()
        );

        try {
            emailService.enviarCorreo(
                    evento.emailTurista(),
                    asunto,
                    mensaje
            );
        } catch (Exception e) {
            System.err.println(
                    "Error enviando correo de cupo asignado: " + e.getMessage()
            );
        }
    }

    // Notifica cuando una reserva vence por no pagar
    // dentro del plazo de dos horas.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviarReservaVencida(ReservaVencidaEvent evento) {

        if (!correoHabilitado || emailService == null) {
            return;
        }

        String asunto = "Tu reserva venció - UMnatural";

        String mensaje = """
            ¡Hola!

            Tu reserva fue cancelada automáticamente
            porque no completaste el pago dentro de las
            2 horas disponibles.

            Actividad: %s
            Fecha: %s
            Hora: %s

            Estado: CANCELADA POR VENCIMIENTO

            No se realizó ningún cobro.

            Si todavía querés participar, podés realizar
            una nueva reserva, siempre que haya cupos disponibles.

            Gracias por utilizar UMnatural.
            """.formatted(
                evento.nombreActividad(),
                evento.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                evento.hora().format(DateTimeFormatter.ofPattern("HH:mm"))
        );

        try {
            emailService.enviarCorreo(
                    evento.emailTurista(),
                    asunto,
                    mensaje
            );
        } catch (Exception e) {
            System.err.println(
                    "Error enviando correo de vencimiento: " + e.getMessage()
            );
        }
    }

    // Confirma que el turista ingresó a la lista de espera.
    // Todavía no tiene un cupo asignado ni debe realizar un pago.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviarInscripcionListaEspera(ListaEsperaRegistradaEvent evento) {

        if (!correoHabilitado || emailService == null) {
            return;
        }

        String asunto = "Estás en la lista de espera - UMnatural";

        String mensaje = """
            ¡Hola!

            Te anotaste correctamente en la lista de espera.

            Actividad: %s
            Fecha: %s
            Hora: %s
            Participantes: %d

            Estado: EN LISTA DE ESPERA

            Por ahora no tenés un cupo reservado
            y no necesitás realizar ningún pago.

            Si se libera un lugar y te lo asignamos,
            recibirás otro correo avisándote que
            tenés 2 horas para completar el pago.

            Podés consultar tu situación en
            "Mis reservas" dentro de UMnatural.

            ¡Gracias por elegir UMnatural!
            """.formatted(
                evento.nombreActividad(),
                evento.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                evento.hora().format(DateTimeFormatter.ofPattern("HH:mm")),
                evento.cantidad()
        );

        try {
            emailService.enviarCorreo(
                    evento.emailTurista(),
                    asunto,
                    mensaje
            );
        } catch (Exception e) {
            System.err.println(
                    "Error enviando correo de lista de espera: " + e.getMessage()
            );
        }
    }
}
