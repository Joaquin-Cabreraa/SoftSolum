package uy.edu.um.proytic2026.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Si alguien sube fotos demasiado pesadas, en vez de la página de error de Spring
// vuelve a la página donde estaba con un mensaje.
@ControllerAdvice
public class ErroresDeSubida {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String fotosMuyPesadas(HttpServletRequest request, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", "Las fotos son muy pesadas: cada una puede tener hasta 5 MB.");
        String desde = request.getHeader("Referer");
        return "redirect:" + (desde != null ? desde : "/panel/prestador/actividades");
    }
}
