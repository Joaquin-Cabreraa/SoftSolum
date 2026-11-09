package uy.edu.um.proytic2026.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// Se ejecuta ANTES de cada pedido a /panel/** y /admin/** (ver WebConfig).
// Si el usuario no tiene el rol necesario, corta el pedido.
@Component
public class ControlDeAccesoInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession sesion = request.getSession(false);
        String rol = (sesion == null) ? null : (String) sesion.getAttribute("usuarioRol");
        String ruta = request.getRequestURI();

        boolean permitido;
        if (ruta.startsWith("/panel/prestador")) {
            permitido = "EMPRESA".equals(rol) || "PARTICULAR".equals(rol) || "PRESTADOR".equals(rol);
        } else if (ruta.startsWith("/panel/turista")) {
            permitido = "TURISTA".equals(rol);
        } else {
            permitido = "ADMINISTRADOR".equals(rol);
        }

        if (permitido) {
            return true;
        }

        if (ruta.startsWith("/admin")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Solo un administrador puede hacer esto.");
        } else {
            response.sendRedirect("/login");
        }
        return false;
    }
}
