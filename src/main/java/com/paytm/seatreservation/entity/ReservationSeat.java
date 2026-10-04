package com.paytm.seatreservation.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "reservation_seats")
@IdClass(ReservationSeatId.class)
public class ReservationSeat {

    @Id
    private UUID reservationId;

    @Id
    private UUID seatId;

    public ReservationSeat() {}

    public ReservationSeat(UUID reservationId, UUID seatId) {
        this.reservationId = reservationId;
        this.seatId = seatId;
    }

    public UUID getReservationId() { return reservationId; }
    public UUID getSeatId() { return seatId; }

    public void setSeatId(UUID seatId) {
        this.seatId = seatId;
    }
}
