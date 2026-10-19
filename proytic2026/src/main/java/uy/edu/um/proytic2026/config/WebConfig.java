package uy.edu.um.proytic2026.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Le dice a Spring en qué rutas tiene que pasar por el control de acceso
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ControlDeAccesoInterceptor controlDeAcceso;

    public WebConfig(ControlDeAccesoInterceptor controlDeAcceso) {
        this.controlDeAcceso = controlDeAcceso;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(controlDeAcceso)
                .addPathPatterns("/panel/**", "/admin/**");
    }
}
