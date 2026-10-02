package vn.com.fis.consentcore.auth;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class AdminAuthExceptionHandler {
    @ExceptionHandler(AuthRejectedException.class)
    ResponseEntity<Map<String, String>> rejected(AuthRejectedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", ex.getMessage()));
    }
}
