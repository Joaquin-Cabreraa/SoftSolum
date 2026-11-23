
package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "umnatural.mail.enabled",
        havingValue = "true"
)
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${umnatural.mail.from}")
    private String remitente;

    public void enviarCorreo(String destinatario, String asunto, String mensaje) {
        SimpleMailMessage correo = new SimpleMailMessage();

        correo.setFrom(remitente);
        correo.setTo(destinatario);
        correo.setSubject(asunto);
        correo.setText(mensaje);

        mailSender.send(correo);
    }
}
