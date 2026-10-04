package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID> {

    @Query(value = """
            SELECT *
            FROM seats
            WHERE show_id = :showId
              AND seat_number IN (:seatNumbers)
            ORDER BY seat_number
            FOR UPDATE
            """, nativeQuery = true)
    List<Seat> lockSeats(
            @Param("showId") UUID showId,
            @Param("seatNumbers") List<String> seatNumbers
    );

    List<Seat> findByShowId(UUID showId);

    @Query("""
        SELECT s
        FROM Seat s
        WHERE s.showId = :showId
        ORDER BY s.seatNumber
        """)
    List<Seat> findByShowIdOrderBySeatNumber(UUID showId);

    @Query(value = """
        SELECT *
        FROM seats
        WHERE id IN (
            SELECT seat_id
            FROM reservation_seats
            WHERE reservation_id = :reservationId
        )
        ORDER BY seat_number
        FOR UPDATE
        """, nativeQuery = true)
    List<Seat> lockSeatsForReservation(UUID reservationId);


}
