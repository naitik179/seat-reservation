package com.paytm.seatreservation.service;

import io.micrometer.core.instrument.MeterRegistry;
import com.paytm.seatreservation.dto.ReservationResponse;
import com.paytm.seatreservation.dto.ReserveRequest;
import com.paytm.seatreservation.entity.*;
import com.paytm.seatreservation.exception.*;
import com.paytm.seatreservation.repository.IdempotencyKeyRepository;
import com.paytm.seatreservation.repository.ReservationRepository;
import com.paytm.seatreservation.repository.ReservationSeatRepository;
import com.paytm.seatreservation.repository.SeatRepository;
import com.paytm.seatreservation.repository.ShowRepository;
import com.paytm.seatreservation.repository.UserShowRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {

    private static final int DEFAULT_PER_USER_LIMIT = 4;

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final UserShowRepository userShowRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final RequestHashService requestHashService;

    public ReservationService(ShowRepository showRepository, SeatRepository seatRepository, ReservationRepository reservationRepository, ReservationSeatRepository reservationSeatRepository, UserShowRepository userShowRepository, IdempotencyKeyRepository idempotencyKeyRepository, RequestHashService requestHashService) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.reservationSeatRepository = reservationSeatRepository;
        this.userShowRepository = userShowRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.requestHashService = requestHashService;
    }

    @Transactional
    public ReservationResponse reserve(UUID showId, String userId, ReserveRequest request) {

        List<String> seats = normalizeSeats(request.seats());

        String requestHash = requestHashService.hashSeats(seats);

        /*
         * Step 1:
         * Create the idempotency record if this is the first request.
         */
        UUID idempotencyId = UUID.randomUUID();

        idempotencyKeyRepository.createIfAbsent(idempotencyId, showId, userId, request.idempotencyKey(), requestHash);

        /*
         * Step 2:
         * Lock the idempotency row.
         *
         * This serializes concurrent requests using the same key.
         */
        IdempotencyKey idempotencyKey = idempotencyKeyRepository.lockKey(showId, userId, request.idempotencyKey()).orElseThrow(() -> new IllegalStateException("Idempotency record was not found"));

        /*
         * Step 3:
         * Same key + different request body = 409.
         */
        if (!idempotencyKey.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException();
        }

        /*
         * Step 4:
         * If the reservation already exists, this is a retry.
         */
        if (idempotencyKey.getReservationId() != null) {

            Reservation existingReservation = reservationRepository.findById(idempotencyKey.getReservationId()).orElseThrow(() -> new IllegalStateException("Reservation referenced by idempotency key does not exist"));

            return toResponse(existingReservation);
        }

        /*
         * Step 5:
         * Load the show.
         */
        Show show = showRepository.findById(showId).orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));

        /*
         * Step 6:
         * Ensure user_show exists.
         *
         * Important for the first concurrent reservation from a user.
         */
        userShowRepository.ensureExists(showId, userId);

        /*
         * Step 7:
         * Lock user_show.
         *
         * This makes the per-user limit concurrency-safe.
         */
        UserShow userShow = userShowRepository.lockUserShow(showId, userId).orElseThrow(() -> new IllegalStateException("user_show record was not found"));

        /*
         * Step 8:
         * Check per-user limit.
         */
        if (userShow.getSeatCount() + seats.size() > DEFAULT_PER_USER_LIMIT) {

            throw new UserLimitException();
        }

        /*
         * Step 9:
         * Lock requested seats in deterministic order.
         */
        List<Seat> lockedSeats = seatRepository.lockSeats(showId, seats);

        /*
         * Step 10:
         * Make sure all requested seats exist.
         */
        if (lockedSeats.size() != seats.size()) {

            List<String> foundSeats = lockedSeats.stream().map(Seat::getSeatNumber).toList();

            List<String> missingSeats = seats.stream().filter(seat -> !foundSeats.contains(seat)).toList();

            throw new SeatNotFoundException(missingSeats);
        }

        /*
         * Step 11:
         * All-or-nothing availability check.
         */
        List<String> unavailableSeats = lockedSeats.stream().filter(seat -> seat.getStatus() != SeatStatus.AVAILABLE).map(Seat::getSeatNumber).toList();

        if (!unavailableSeats.isEmpty()) {
            throw new SeatTakenException(unavailableSeats);
        }

        /*
         * Step 12:
         * Calculate amount in integer paise.
         */
        long amountPaise = Math.multiplyExact(show.getPricePaise(), seats.size());

        /*
         * Step 13:
         * Create reservation.
         */
        Reservation reservation = new Reservation();

        reservation.setShowId(showId);
        reservation.setUserId(userId);
        reservation.setAmountPaise(amountPaise);
        reservation.setStatus(ReservationStatus.CONFIRMED);

        reservation = reservationRepository.save(reservation);

        /*
         * Step 14:
         * Confirm every seat.
         */
        for (Seat seat : lockedSeats) {

            seat.setStatus(SeatStatus.CONFIRMED);

            ReservationSeat reservationSeat = new ReservationSeat(reservation.getId(), seat.getId());

            reservationSeatRepository.save(reservationSeat);
        }

        /*
         * Step 15:
         * Update user's reserved-seat count.
         */
        userShow.setSeatCount(userShow.getSeatCount() + seats.size());

        /*
         * Step 16:
         * Complete idempotency record.
         */
        idempotencyKey.setReservationId(reservation.getId());

        idempotencyKey.setResponseStatus(201);

        return toResponse(reservation);
    }

    private List<String> normalizeSeats(List<String> seats) {

        return seats.stream().map(String::trim).filter(s -> !s.isBlank()).distinct().sorted().toList();
    }

    private ReservationResponse toResponse(Reservation reservation) {

        List<String> seats = reservationSeatRepository.findSeatNumbersByReservationId(reservation.getId());

        return new ReservationResponse(reservation.getId(), reservation.getShowId(), reservation.getUserId(), seats, reservation.getAmountPaise(), reservation.getStatus().name());
    }

    @Transactional
    public void cancelReservation(UUID reservationId, String userId) {

        Reservation reservation = reservationRepository.lockById(reservationId).orElseThrow(() -> new ReservationNotFoundException("Reservation not found: " + reservationId));

        if (!reservation.getUserId().equals(userId)) {
            throw new AccessDeniedException("You cannot cancel this reservation");
        }

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new ReservationCancelledException("Reservation is already cancelled");
        }

        List<Seat> seats = seatRepository.lockSeatsForReservation(reservationId);

        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.AVAILABLE);
        }

        seatRepository.saveAll(seats);

        UserShow userShow = userShowRepository.lockUserShow(reservation.getShowId(), userId).orElseThrow();

        userShow.setSeatCount(userShow.getSeatCount() - seats.size());

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(Instant.now());

        reservationRepository.save(reservation);
        userShowRepository.save(userShow);
    }
}