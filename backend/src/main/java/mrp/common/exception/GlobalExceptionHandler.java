package mrp.common.exception;

import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ConcurrencyFailureException.class)
    ProblemDetail busy(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Hệ thống đang bận, vui lòng thử lại");
    }

    private boolean concurrencySqlState(Exception ex) {
        String state = extractSqlState(ex);
        return "40P01".equals(state) || "40001".equals(state);
    }
    @ExceptionHandler(BadRequestException.class)
    ProblemDetail badRequest(BadRequestException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ProblemDetail invalidParameter(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Tham số không hợp lệ");
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ProblemDetail forbidden(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này");
    }
    private static final Set<String> CONFLICT_SQL_STATES = Set.of("23505", "23503", "23514", "P0001");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ");
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraint(ConstraintViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail unauthorized(InvalidCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    ProblemDetail business(BusinessException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(Exception ex) {
        if (concurrencySqlState(ex)) return busy(ex);
        String sqlState = extractSqlState(ex);
        if (sqlState != null && !CONFLICT_SQL_STATES.contains(sqlState)) {
            log.error("Unhandled SQL data integrity error (SQLState={}): ", sqlState, ex);
            return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi không mong muốn");
        }
        log.warn("Data integrity violation (SQLState={}): {}", sqlState, ex.getMessage());
        String detail = extractBusinessMessage(ex);
        if (detail == null) {
            detail = "Dữ liệu vi phạm quy tắc nghiệp vụ, đang được sử dụng hoặc bị trùng";
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformed(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body không hợp lệ");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        if (concurrencySqlState(ex)) return busy(ex);
        log.error("Unhandled internal server error: ", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi không mong muốn");
    }

    private static String extractSqlState(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
            cause = cause.getCause();
        }
        return null;
    }

    private static String extractBusinessMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SQLException sqlException) {
                String state = sqlException.getSQLState();
                if ("P0001".equals(state) && sqlException.getMessage() != null) {
                    String msg = sqlException.getMessage();
                    if (msg.startsWith("ERROR: ")) {
                        msg = msg.substring(7);
                    }
                    int newline = msg.indexOf('\n');
                    if (newline > 0) {
                        msg = msg.substring(0, newline);
                    }
                    return msg.trim();
                }
            }
            cause = cause.getCause();
        }
        return null;
    }
}
