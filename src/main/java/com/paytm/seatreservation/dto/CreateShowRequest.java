package com.paytm.seatreservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
public record CreateShowRequest(

        @NotBlank(message = "Show name is required")
        @Size(max = 255, message = "Show name must not exceed 255 characters")
        String name,

        @NotEmpty(message = "At least one seat is required")
        List<
                @NotBlank(message = "Seat number cannot be blank")
                @Size(max = 50, message = "Seat number must not exceed 50 characters")
                        String
                > seats,

        @NotNull(message = "Price in paise is required")
        @Positive(message = "Price in paise must be greater than zero")
        Long pricePaise
) {
}