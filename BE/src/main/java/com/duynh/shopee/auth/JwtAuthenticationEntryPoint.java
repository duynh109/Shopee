package com.duynh.shopee.auth;

import java.io.IOException;

import com.duynh.shopee.exception.ProblemDetailWriter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Chạy khi request chưa đăng nhập mà chạm vào endpoint cần đăng nhập -> 401.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailWriter problemDetailWriter;

    public JwtAuthenticationEntryPoint(ProblemDetailWriter problemDetailWriter) {
        this.problemDetailWriter = problemDetailWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        TokenError error = (TokenError) request.getAttribute(JwtAuthenticationFilter.TOKEN_ERROR_ATTRIBUTE);
        if (error == null) {
            error = TokenError.MISSING;
        }

        problemDetailWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                error.getType(), error.getTitle());
    }
}
