package com.duynh.shopee.auth;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Nơi filter ghi lại lý do token bị từ chối, để JwtAuthenticationEntryPoint đọc lại.
     */
    public static final String TOKEN_ERROR_ATTRIBUTE = "tokenError";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null) {
            try {
                authenticate(jwtService.parse(token));
            } catch (ExpiredJwtException ex) {
                request.setAttribute(TOKEN_ERROR_ATTRIBUTE, TokenError.EXPIRED);
            } catch (JwtException | IllegalArgumentException ex) {
                request.setAttribute(TOKEN_ERROR_ATTRIBUTE, TokenError.INVALID);
            }
        }

        // Luôn cho request đi tiếp, kể cả khi token sai.
        // Việc chặn là của SecurityConfig, việc trả lỗi là của
        // JwtAuthenticationEntryPoint.
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }

    private void authenticate(AccessTokenClaims claims) {
        UserDetails userDetails = User
                .withUsername(claims.email())
                .password("")
                .authorities("ROLE_" + claims.role())
                .build();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
