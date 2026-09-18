package org.example.ecommerce.auth;

import java.io.IOException;

import org.example.ecommerce.exception.ProblemDetailWriter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Chạy khi đã đăng nhập nhưng không đủ quyền -> 403.
 */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailWriter problemDetailWriter;

    public ApiAccessDeniedHandler(ProblemDetailWriter problemDetailWriter) {
        this.problemDetailWriter = problemDetailWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        problemDetailWriter.write(request, response, HttpStatus.FORBIDDEN,
                "/errors/forbidden", "Bạn không có quyền thực hiện thao tác này");
    }
}
