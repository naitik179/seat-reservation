package com.paytm.seatreservation.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SeatTakenException.class)
    public ResponseEntity<?> handleSeatTaken(
            SeatTakenException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "SEAT_TAKEN",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(UserLimitException.class)
    public ResponseEntity<?> handleUserLimit(
            UserLimitException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "PER_USER_LIMIT",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<?> handleIdempotencyConflict(
            IdempotencyConflictException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_CONFLICT",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(SeatNotFoundException.class)
    public ResponseEntity<?> handleSeatNotFound(
            SeatNotFoundException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "SEAT_NOT_FOUND",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(ShowNotFoundException.class)
    public ResponseEntity<?> handleShowNotFound(
            ShowNotFoundException exception,
            HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "timestamp", Instant.now(),
                        "status", 404,
                        "code", "SHOW_NOT_FOUND",
                        "message", exception.getMessage()
                ));
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<?> handleReservationNotFound(
            ReservationNotFoundException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "RESERVATION_NOT_FOUND",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(ReservationCancelledException.class)
    public ResponseEntity<?> handleReservationCancelled(
            ReservationCancelledException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "RESERVATION_ALREADY_CANCELLED",
                exception.getMessage(),
                request
        );
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("code", code);
        body.put("message", message);

        String requestId = request.getHeader("X-Request-ID");

        if (requestId != null && !requestId.isBlank()) {
            body.put("request_id", requestId);
        }

        return ResponseEntity
                .status(status)
                .body(body);
    }
}