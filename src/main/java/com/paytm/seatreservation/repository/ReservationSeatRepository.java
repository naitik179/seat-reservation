package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.ReservationSeat;
import com.paytm.seatreservation.entity.ReservationSeatId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, ReservationSeatId> {
    @Query(value = """
            SELECT s.seat_number
            FROM reservation_seats rs
            JOIN seats s ON s.id = rs.seat_id
            WHERE rs.reservation_id = :reservationId
            ORDER BY s.seat_number
            """, nativeQuery = true)
    List<String> findSeatNumbersByReservationId(
            @Param("reservationId") UUID reservationId
    );}
