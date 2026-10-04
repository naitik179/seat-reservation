package com.paytm.seatreservation.service;

import com.paytm.seatreservation.dto.CreateShowRequest;
import com.paytm.seatreservation.dto.ShowResponse;
import com.paytm.seatreservation.entity.Seat;
import com.paytm.seatreservation.entity.SeatStatus;
import com.paytm.seatreservation.entity.Show;
import com.paytm.seatreservation.exception.ShowNotFoundException;
import com.paytm.seatreservation.repository.SeatRepository;
import com.paytm.seatreservation.repository.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;

    public static final int perUserLimit = 4;

    public ShowService(
            ShowRepository showRepository,
            SeatRepository seatRepository
    ) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {

        Show show = new Show();

        show.setName(request.name());
        show.setPricePaise(request.pricePaise());
        show.setPerUserLimit(perUserLimit);

        Show savedShow = showRepository.save(show);

        List<Seat> seats = new ArrayList<>();

        for (String seatNumber : request.seats()) {

            Seat seat = new Seat();

            seat.setShowId(savedShow.getId());
            seat.setSeatNumber(seatNumber);
            seat.setStatus(SeatStatus.AVAILABLE);

            seats.add(seat);
        }

        seatRepository.saveAll(seats);

        return buildShowResponse(savedShow, seats);
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(UUID showId) {

        Show show = showRepository.findById(showId)
                .orElseThrow(() ->
                        new ShowNotFoundException(
                                "Show not found: " + showId
                        ));

        List<Seat> seats =
                seatRepository.findByShowIdOrderBySeatNumber(showId);

        return buildShowResponse(show, seats);
    }

    private ShowResponse buildShowResponse(
            Show show,
            List<Seat> seats
    ) {

        int available = 0;
        int held = 0;
        int confirmed = 0;

        List<ShowResponse.SeatResponse> seatResponses =
                new ArrayList<>();

        for (Seat seat : seats) {

            if (seat.getStatus() == SeatStatus.AVAILABLE) {
                available++;
            } else if (seat.getStatus() == SeatStatus.HELD) {
                held++;
            } else if (seat.getStatus() == SeatStatus.CONFIRMED) {
                confirmed++;
            }

            seatResponses.add(
                    new ShowResponse.SeatResponse(
                            seat.getSeatNumber(),
                            seat.getStatus().name()
                    )
            );
        }

        return new ShowResponse(
                show.getId(),
                show.getName(),
                show.getPricePaise(),
                seats.size(),
                available,
                held,
                confirmed,
                seatResponses
        );
    }
}