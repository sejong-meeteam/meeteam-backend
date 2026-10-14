package com.wardk.meeteam_backend.global.exception.handler;

import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 1) CustomException 처리
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {

        log.error("CustomException 발생={}",e.getMessage());

        ErrorCode errorCode = e.getErrorCode();
        ErrorResponse response = ErrorResponse.getResponse(errorCode.getCode(), errorCode.getMessage());

        return ResponseEntity.status(errorCode.getStatus()).body(response);

    }

    /**
     * 2) IllegalArgumentException을 400 에러로 처리
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("IllegalArgumentException 발생: {}", e.getMessage());

        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;  // BAD_REQUEST (400)
        ErrorResponse response = ErrorResponse.getResponse(errorCode.getCode(), e.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 3) 잘못된 파라미터 타입을 400 에러로 처리
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.error("MethodArgumentTypeMismatchException 발생: {}", e.getMessage());

        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;  // BAD_REQUEST (400)
        ErrorResponse response = ErrorResponse.getResponse(errorCode.getCode(), "잘못된 요청 파라미터입니다.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 4) Validation 에러를 400 에러로 처리 (새로 추가)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.error("Validation Exception 발생: {}", e.getMessage());

        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;  // BAD_REQUEST (400)

        // 첫 번째 validation 에러 메시지 사용
        String errorMessage = "입력값이 올바르지 않습니다.";
        if (e.getBindingResult().hasFieldErrors()) {
            errorMessage = e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        }

        ErrorResponse response = ErrorResponse.getResponse(errorCode.getCode(), errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }


    /**
     * 5) 업로드 파일 크기 초과 처리 (멀티파트 한도 초과)
     * 프론트는 이 413 응답(FILE413)을 받고 "파일이 너무 큽니다" 안내를 띄우면 된다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("파일 업로드 크기 초과: {}", e.getMessage());

        ErrorCode errorCode = ErrorCode.FILE_TOO_LARGE;  // PAYLOAD_TOO_LARGE (413)
        ErrorResponse response = ErrorResponse.getResponse(errorCode.getCode(), errorCode.getMessage());

        return ResponseEntity.status(errorCode.getStatus()).body(response);
    }

    /**
     * 6) 그 외 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        log.error("Unhandled Exception 발생: {}", e.getMessage(), e);

        // 예상치 못한 예외 => 500
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }

}
