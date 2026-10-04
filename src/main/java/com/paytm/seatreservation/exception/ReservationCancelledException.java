package com.paytm.seatreservation.exception;

public class ReservationCancelledException extends RuntimeException {

    public ReservationCancelledException(String message) {
        super(message);
    }
}
