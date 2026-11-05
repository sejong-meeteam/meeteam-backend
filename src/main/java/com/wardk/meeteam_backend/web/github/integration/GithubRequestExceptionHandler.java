package com.wardk.meeteam_backend.web.github.integration;

import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** Never reflect exception/SQL/upstream text from GitHub endpoints to clients or argument logs. */
@RestControllerAdvice(basePackages="com.wardk.meeteam_backend.web.github")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GithubRequestExceptionHandler {
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> custom(CustomException exception){
        var code=exception.getErrorCode();return response(code.getStatus(),code.getCode(),exception.getMessage());
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,MethodArgumentNotValidException.class})
    public ResponseEntity<ErrorResponse> malformed(Exception ignored){return error(ErrorCode.GITHUB_INVALID_REQUEST);}
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> status(ResponseStatusException exception){
        return exception.getStatusCode().value()==400?error(ErrorCode.GITHUB_INVALID_REQUEST):error(ErrorCode.INTERNAL_SERVER_ERROR);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ignored){return error(ErrorCode.INTERNAL_SERVER_ERROR);}
    private static ResponseEntity<ErrorResponse> error(ErrorCode code){return response(code.getStatus(),code.getCode(),code.getMessage());}
    private static ResponseEntity<ErrorResponse> response(HttpStatusCode status,String code,String message){
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer")
                .body(ErrorResponse.getResponse(code,message));
    }
}
