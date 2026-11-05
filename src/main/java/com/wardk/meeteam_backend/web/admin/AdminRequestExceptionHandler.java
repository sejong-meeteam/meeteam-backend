package com.wardk.meeteam_backend.web.admin;

import com.wardk.meeteam_backend.global.response.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {AdminQueryController.class, AdminMutationController.class, AdminAuditController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminRequestExceptionHandler {
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.getResponse("ADMIN_INVALID_REQUEST", "요청 내용을 확인해 주세요."));
    }

    @ExceptionHandler({org.springframework.dao.ConcurrencyFailureException.class,
            jakarta.persistence.OptimisticLockException.class, jakarta.persistence.PessimisticLockException.class,
            jakarta.persistence.LockTimeoutException.class})
    public ResponseEntity<ErrorResponse> conflict(Exception exception) {
        return ResponseEntity.status(409).body(ErrorResponse.getResponse("ADMIN_INVALID_STATE",
                "다른 요청으로 상태가 변경되었거나 처리 중입니다. 새로고침 후 다시 시도해 주세요."));
    }
    @ExceptionHandler({org.springframework.dao.DataAccessException.class, jakarta.persistence.PersistenceException.class,
            org.springframework.transaction.TransactionException.class})
    public ResponseEntity<ErrorResponse> storageFailure(Exception exception) {
        return ResponseEntity.internalServerError().body(ErrorResponse.getResponse("COMMON500",
                "서버에 문제가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }
}
