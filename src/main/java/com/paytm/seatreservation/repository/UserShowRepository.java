package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.UserShow;
import com.paytm.seatreservation.entity.UserShowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserShowRepository extends JpaRepository<UserShow, UserShowId> {

    @Modifying
    @Query(value = """
            INSERT INTO user_show(show_id, user_id, seat_count)
            VALUES (:showId, :userId, 0)
            ON CONFLICT (show_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int ensureExists(
            @Param("showId") UUID showId,
            @Param("userId") String userId
    );

    @Query(value = """
            SELECT *
            FROM user_show
            WHERE show_id = :showId
              AND user_id = :userId
            FOR UPDATE
            """, nativeQuery = true)
    Optional<UserShow> lockUserShow(
            @Param("showId") UUID showId,
            @Param("userId") String userId
    );
}
