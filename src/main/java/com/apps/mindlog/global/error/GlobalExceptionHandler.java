package com.apps.mindlog.global.error;

import com.apps.mindlog.global.error.exception.ApiException;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INTERNAL_DETAIL = "요청을 처리하는 중 오류가 발생했습니다.";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException exception, WebRequest request) {
        ErrorType type = exception.getErrorType();
        String detail = type.getStatus().is5xxServerError() ? INTERNAL_DETAIL : exception.getMessage();
        return respond(problem(type, detail, request), new HttpHeaders());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        List<ValidationError> errors = exception.getBindingResult().getAllErrors().stream()
                .map(error -> new ValidationError(
                        error instanceof org.springframework.validation.FieldError fieldError
                                ? fieldError.getField() : "body",
                        reason(error.getCode())))
                .distinct()
                .sorted(Comparator.comparing(ValidationError::field).thenComparing(ValidationError::reason))
                .toList();
        ProblemDetail body = problem(ErrorType.VALIDATION_ERROR, "입력값을 확인해 주세요.", request);
        body.setProperty("errors", errors);
        return respond(body, headers);
    }

    // Spring MVC의 400/404/405/415 등을 포괄 예외 처리에서 500으로 바꾸지 않는다.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            return respond(problem(ErrorType.INTERNAL_ERROR, INTERNAL_DETAIL, request), headers);
        }
        if (status.value() == 400) {
            return respond(problem(ErrorType.VALIDATION_ERROR, "요청 형식과 입력값을 확인해 주세요.", request), headers);
        }
        if (status.value() == 404) {
            return respond(problem(ErrorType.RESOURCE_NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.", request), headers);
        }
        // 405 Allow, 415 Accept 등 프레임워크의 상태와 헤더를 보존한다.
        ProblemDetail detail = ProblemDetail.forStatus(status);
        detail.setInstance(instance(request));
        return respond(detail, headers);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
        // 예외 메시지에는 일기 원문·SQL 등 민감한 값이 포함될 수 있다.
        log.error("Unhandled request exception: {}", exception.getClass().getName());
        return respond(problem(ErrorType.INTERNAL_ERROR, INTERNAL_DETAIL, request), new HttpHeaders());
    }

    private static ProblemDetail problem(ErrorType type, String detail, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(type.getStatus(), detail);
        body.setType(type.getType());
        body.setTitle(type.getTitle());
        body.setInstance(instance(request));
        return body;
    }

    private static URI instance(WebRequest request) {
        // 쿼리 문자열은 오류 응답에 포함하지 않는다.
        return URI.create(((ServletWebRequest) request).getRequest().getRequestURI());
    }

    private static ResponseEntity<Object> respond(ProblemDetail body, HttpHeaders originalHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(originalHeaders);
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(body, headers, body.getStatus());
    }

    private static String reason(String code) {
        if (code == null) return "invalid";
        return switch (code) {
            case "NotNull", "NotBlank", "NotEmpty" -> "required";
            case "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero",
                    "Negative", "NegativeOrZero" -> "range";
            case "Size" -> "size";
            case "Email", "Pattern" -> "format";
            default -> "invalid";
        };
    }

    public record ValidationError(String field, String reason) { }
}
