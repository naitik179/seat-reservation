package com.paytm.seatreservation.dto;

import java.util.List;
import java.util.UUID;

public record ShowResponse(
        UUID id,
        String name,
        long pricePaise,
        int totalSeats,
        int availableSeats,
        int heldSeats,
        int confirmedSeats,
        List<SeatResponse> seats
) {

    public record SeatResponse(
            String seatNumber,
            String status
    ) {
    }
}