package com.paytm.seatreservation.exception;

public class UserLimitException extends RuntimeException {

    public UserLimitException() {
        super("User has reached the maximum allowed seats for this show");
    }
}
