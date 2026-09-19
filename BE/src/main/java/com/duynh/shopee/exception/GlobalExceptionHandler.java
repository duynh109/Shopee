package com.duynh.shopee.exception;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "/errors/not-found", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "/errors/conflict", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied() {
        return problem(HttpStatus.FORBIDDEN, "/errors/forbidden", "Bạn không có quyền thực hiện thao tác này");
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getType(), ex.getMessage());
    }

    @ExceptionHandler(FieldValidationException.class)
    public ProblemDetail handleFieldValidation(FieldValidationException ex) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_CONTENT, "/errors/validation", ex.getMessage());
        pd.setProperty("errors", ex.getErrors());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBeanValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_CONTENT, "/errors/validation", "Dữ liệu không hợp lệ");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "/errors/not-found", "Không tìm thấy đường dẫn này");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "/errors/method-not-allowed",
                "Đường dẫn này không hỗ trợ method " + ex.getMethod());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "/errors/unsupported-media-type",
                "Content-Type không được hỗ trợ, hãy dùng application/json");
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ProblemDetail handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "/errors/bad-request", "Request không đọc được");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        logger.error("An unexpected error occurred", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "/errors/internal-server-error",
                "Đã có lỗi xảy ra, vui lòng thử lại sau");
    }

    private ProblemDetail problem(HttpStatus status, String type, String title) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setType(URI.create(type));
        pd.setTitle(title);
        return pd;
    }
}
