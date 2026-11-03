package br.com.vanroute.backend.config;

import br.com.vanroute.backend.services.TermsService;

import br.com.vanroute.backend.models.user.enums.UserStatus;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.repositories.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthenticatorFilter extends OncePerRequestFilter {

    private final TokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final TermsService termsService;

    public JwtAuthenticatorFilter(TokenProvider tokenProvider, UserRepository userRepository, TermsService termsService) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.termsService = termsService;
    }

    // sem ter aceitado a versao atual dos termos, o usuario logado so consegue ver e aceitar os termos, ver a propria
    // conta, sair, exportar os dados e excluir a conta. O resto responde 403 com code TERMS_NOT_ACCEPTED
    static boolean allowedWithoutTerms(String method, String path) {
        return path.startsWith("/api/terms")
                || path.startsWith("/api/auth/")
                || ("GET".equals(method) && (path.equals("/api/users/me") || path.equals("/api/users/me/export")))
                || ("DELETE".equals(method) && path.equals("/api/users/me"));
    }

    // token antigo (sub = CPF), usuario apagado ou conta excluida (INACTIVE): segue sem autenticacao (401 nas rotas protegidas)
    private Optional<User> findUser(String subject) {
        try {
            return userRepository.findById(UUID.fromString(subject)).filter(user -> user.getStatus() != UserStatus.INACTIVE);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }


    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");
        String token = null;
        
        if(StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")){
            token = authorizationHeader.substring(7);
        } else {
            token = request.getParameter("token");
        }
        
        if(StringUtils.hasText(token) && tokenProvider.isTokenValid(token)){
            // o sub e o id do usuario
            Optional<User> user = findUser(tokenProvider.getSubject(token));
            if (user.isPresent()) {
                if (!allowedWithoutTerms(request.getMethod(), request.getRequestURI())
                        && !termsService.acceptedCurrent(user.get().getId())) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":\"TERMS_NOT_ACCEPTED\",\"version\":\"" + TermsService.CURRENT_VERSION
                            + "\",\"message\":\"Aceite a versão atual dos Termos de Uso para continuar\"}");
                    return;
                }
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        user.get(), null, user.get().getAuthorities()));
            }
        }
        filterChain.doFilter(request, response);
    }
}
