package com.paytm.seatreservation.exception;

import java.util.List;

public class SeatNotFoundException extends RuntimeException {

    private final List<String> seats;

    public SeatNotFoundException(List<String> seats) {
        super("Requested seats do not exist: " + seats);
        this.seats = seats;
    }

    public List<String> getSeats() {
        return seats;
    }
}
