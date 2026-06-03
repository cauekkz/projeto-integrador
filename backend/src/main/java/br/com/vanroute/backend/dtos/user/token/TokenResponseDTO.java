package br.com.vanroute.backend.dtos.user.token;

public record TokenResponseDTO (String token, long expiresIn){
}
