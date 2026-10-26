package br.com.vanroute.backend.dtos.user.token;

// token: JWT de acesso (expiresIn ms). refreshToken: troca por um par novo em POST /api/auth/refresh (refreshExpiresIn ms)
public record TokenResponseDTO (String token, long expiresIn, String refreshToken, long refreshExpiresIn){
}
