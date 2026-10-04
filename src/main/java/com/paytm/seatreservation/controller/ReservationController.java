package com.paytm.seatreservation.controller;

import com.paytm.seatreservation.dto.ReservationResponse;
import com.paytm.seatreservation.dto.ReserveRequest;
import com.paytm.seatreservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/shows")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/{showId}/reserve")
    public ResponseEntity<ReservationResponse> reserve(@PathVariable UUID showId, @Valid @RequestBody ReserveRequest request,
                                                       Authentication authentication) {

        String userId = authentication.getName();

        ReservationResponse response = reservationService.reserve(showId, userId, request);

        return ResponseEntity.created(URI.create("/reservations/" + response.reservationId())).body(response);
    }

    @DeleteMapping("/reservations/{reservationId}")
    public ResponseEntity<Void> cancelReservation(@PathVariable UUID reservationId, Authentication authentication) {

        reservationService.cancelReservation(reservationId, authentication.getName());

        return ResponseEntity.noContent().build();
    }
}
