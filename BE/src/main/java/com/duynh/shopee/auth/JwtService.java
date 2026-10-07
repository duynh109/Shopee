package com.duynh.shopee.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    private static final String ROLE_CLAIM = "role";

    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtService(@Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-seconds}") long expirationSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim(ROLE_CLAIM, role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationSeconds * 1000))
                .signWith(secretKey)
                .compact();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    public AccessTokenClaims parse(String token) {
        Claims claims = parseClaims(token);
        String role = claims.get(ROLE_CLAIM, String.class);
        if (role == null) {
            throw new MalformedJwtException("Token thiếu claim role");
        }
        return new AccessTokenClaims(claims.getSubject(), role);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
