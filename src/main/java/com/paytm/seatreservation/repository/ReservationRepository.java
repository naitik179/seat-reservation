package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @Query(value = """
        SELECT *
        FROM reservations
        WHERE id = :reservationId
        FOR UPDATE
        """, nativeQuery = true)
    Optional<Reservation> lockById(UUID reservationId);
}
