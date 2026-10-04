package com.paytm.seatreservation.exception;

import java.util.List;

public class SeatTakenException extends RuntimeException {

    private final List<String> seats;

    public SeatTakenException(List<String> seats) {
        super("One or more requested seats are no longer available: " + seats);
        this.seats = seats;
    }

    public List<String> getSeats() {
        return seats;
    }
}