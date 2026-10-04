package com.paytm.seatreservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReserveRequest(
        @NotEmpty(message = "seats must not be empty")
        List<@NotBlank(message = "seat number must not be blank") String> seats,

        @NotBlank(message = "idempotency key must not be blank")
        String idempotencyKey
) {
}