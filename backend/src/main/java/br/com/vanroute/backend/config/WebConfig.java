package br.com.vanroute.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // quem pode chamar a API e abrir os WebSockets. Padrao de desenvolvimento: localhost e rede local
    // (celular pelo proxy do Angular). Em producao: ALLOWED_ORIGINS=https://app.dominio.com
    public static final String ALLOWED_ORIGINS = "${app.allowed-origins}";

    private final String[] allowedOrigins;

    public WebConfig(@Value(ALLOWED_ORIGINS) String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true);
    }

}
