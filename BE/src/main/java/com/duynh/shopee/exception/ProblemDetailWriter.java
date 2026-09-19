package com.duynh.shopee.exception;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Tự ghi ProblemDetail vào response.
 *
 * GlobalExceptionHandler chỉ cần return ProblemDetail rồi Spring lo phần còn lại,
 * nhưng lỗi phát sinh trong filter thì không đi qua Spring MVC nên phải tự ghi tay.
 */
@Component
public class ProblemDetailWriter {

    private final ObjectMapper objectMapper;

    public ProblemDetailWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, String type, String title) throws IOException {

        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
