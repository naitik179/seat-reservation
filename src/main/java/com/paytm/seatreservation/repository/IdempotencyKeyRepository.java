package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO idempotency_keys
                (id, show_id, user_id, idempotency_key, request_hash,
                 reservation_id, response_status, created_at)
            VALUES
                (:id, :showId, :userId, :idempotencyKey, :requestHash,
                 NULL, NULL, CURRENT_TIMESTAMP)
            ON CONFLICT (show_id, user_id, idempotency_key)
            DO NOTHING
            """, nativeQuery = true)
    int createIfAbsent(
            @Param("id") UUID id,
            @Param("showId") UUID showId,
            @Param("userId") String userId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash
    );

    @Query(value = """
            SELECT *
            FROM idempotency_keys
            WHERE show_id = :showId
              AND user_id = :userId
              AND idempotency_key = :idempotencyKey
            FOR UPDATE
            """, nativeQuery = true)
    Optional<IdempotencyKey> lockKey(
            @Param("showId") UUID showId,
            @Param("userId") String userId,
            @Param("idempotencyKey") String idempotencyKey
    );
}
