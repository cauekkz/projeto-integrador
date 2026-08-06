package br.com.vanroute.backend.config;

import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

@Component
public class TokenProvider {

    @Value("${spring.jwt.expiration}")
    private Long expirationTime;
    @Value("${spring.jwt.key}")
    private String key;

    public String generateToken(Authentication authentication){
        UserDetails user = (UserDetails) authentication.getPrincipal();
        List<String> roles = user.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return buildToken(user.getUsername(), roles);
    }

    private String buildToken(String username, List<String> roles) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationTime);
        return Jwts.builder()
                .subject(username)
                .issuedAt(expiration)
                .signWith(getSignKey())
                .claim("roles", roles)
                .compact();
    }

    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(key.getBytes());
    }

    public boolean isTokenValid(String token){
        try{
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Claims getClaims(String token){
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUsername(String token){
        return getClaims(token).getSubject();
    }

}
