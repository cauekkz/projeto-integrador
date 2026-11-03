package br.com.vanroute.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticatorFilter filter;

    public SecurityConfig(JwtAuthenticatorFilter filter) {
        this.filter = filter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session
                        -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                }))
                // a primeira regra que bate vale. Por perfil aqui; quem e parte de cada contrato, chat ou viagem
                // continua sendo conferido nos services
                .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // publicas: login, cadastro, codigos de e-mail, esqueci a senha e a documentacao
                .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                .requestMatchers(
                        HttpMethod.POST,
                        "/api/responsible/auth/**",
                        "/api/driver/auth/**"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/terms").permitAll()
                .requestMatchers(
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-ui.html"
                ).permitAll()
                // so motorista
                .requestMatchers(
                        "/api/driver/groups/**",
                        "/api/driver/link-code",
                        "/api/driver/pix-key",
                        "/api/route/**",
                        "/api/school/**",
                        "/api/vehicle/**"
                ).hasRole("DRIVER")
                .requestMatchers(HttpMethod.GET, "/api/chats/*/students").hasRole("DRIVER")
                .requestMatchers(HttpMethod.POST, "/api/chats/*/contract").hasRole("DRIVER")
                .requestMatchers(HttpMethod.PUT, "/api/chats/*/contracts/*/withdraw").hasRole("DRIVER")
                .requestMatchers(HttpMethod.PUT, "/api/payments/*/paid", "/api/payments/*/cancel").hasRole("DRIVER")
                .requestMatchers(HttpMethod.POST, "/api/trip/start/*", "/api/trip/*/finish", "/api/trip/*/occurrences").hasRole("DRIVER")
                .requestMatchers(HttpMethod.PUT, "/api/trip/*/student/**").hasRole("DRIVER")
                .requestMatchers(HttpMethod.GET, "/api/trip/route/*/today").hasRole("DRIVER")
                // so responsavel
                .requestMatchers("/api/student/**", "/api/invite-code/**").hasRole("RESPONSIBLE")
                .requestMatchers(HttpMethod.PUT, "/api/chats/*/contracts/*/accept", "/api/chats/*/contracts/*/reject").hasRole("RESPONSIBLE")
                .requestMatchers(HttpMethod.PUT, "/api/driver/*/rating").hasRole("RESPONSIBLE")
                .requestMatchers(HttpMethod.DELETE, "/api/driver/*/rating").hasRole("RESPONSIBLE")
                // os dois perfis, logados: chat, contrato e cancelamento, notificacoes, parcelas (lista), arquivos,
                // historico e ocorrencias da viagem, perfil do motorista, a propria conta e os WebSockets (?token=)
                .anyRequest().authenticated()
                )
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) {
        return configuration.getAuthenticationManager();
    }
}
