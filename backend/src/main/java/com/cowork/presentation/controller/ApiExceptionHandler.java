package com.cowork.presentation.controller;

import com.cowork.business.exception.AuthenticationFailedException;
import com.cowork.business.exception.ConflictException;
import com.cowork.business.exception.DomainException;
import com.cowork.business.exception.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
class ApiExceptionHandler {

    /** UC01, UC23: sai tài khoản / mật khẩu, tài khoản bị khóa. */
    @ExceptionHandler(AuthenticationFailedException.class)
    ProblemDetail authenticationFailed(AuthenticationFailedException e) {
        return problem(HttpStatus.UNAUTHORIZED, e.code(), e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.code(), e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, e.code(), e.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, e.code(), e.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate() {
        return problem(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "Dữ liệu vừa bị thao tác khác thay đổi, vui lòng thử lại");
    }

    /** Vi phạm ràng buộc ở tầng database — bình thường code đã chặn trước, đây là lớp bảo vệ cuối. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataIntegrity(DataIntegrityViolationException e) {
        String cause = String.valueOf(e.getMostSpecificCause().getMessage());
        if (cause.contains("ex_booking_no_overlap")) {
            return problem(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE", "Vị trí đã có người đặt trong khung giờ này");
        }
        return problem(HttpStatus.CONFLICT, "DATA_CONFLICT", "Dữ liệu vi phạm ràng buộc của hệ thống");
    }

    /** Dữ liệu sai lọt xuống tới entity (ví dụ số tiền âm) — tầng business lẽ ra đã chặn trước. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail illegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_INPUT", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail);
    }

    /** Thiếu tham số bắt buộc trên URL, ví dụ gọi /api/availability không có branchId. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail missingParameter(MissingServletRequestParameterException e) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", e.getParameterName() + ": bắt buộc phải có");
    }

    /** Body không đọc được: JSON hỏng, giá trị enum không tồn tại (ví dụ role = "BOSS"), ngày giờ sai định dạng. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableBody() {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Nội dung request không hợp lệ (JSON, kiểu dữ liệu hoặc giá trị enum)");
    }

    /** Tham số sai kiểu: type không thuộc enum, ngày giờ sai định dạng ISO, id không phải số. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parameterTypeMismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", e.getName() + ": giá trị không hợp lệ '" + e.getValue() + "'");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
