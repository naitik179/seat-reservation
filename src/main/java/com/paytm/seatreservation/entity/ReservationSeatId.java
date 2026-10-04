package com.paytm.seatreservation.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ReservationSeatId implements Serializable {
    private UUID reservationId;
    private UUID seatId;

    public ReservationSeatId() {}

    public ReservationSeatId(UUID reservationId, UUID seatId) {
        this.reservationId = reservationId;
        this.seatId = seatId;
    }

    public UUID getReservationId() { return reservationId; }
    public UUID getSeatId() { return seatId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReservationSeatId that)) return false;
        return Objects.equals(reservationId, that.reservationId)
                && Objects.equals(seatId, that.seatId);
    }

    @Override
    public int hashCode() { return Objects.hash(reservationId, seatId); }
}
