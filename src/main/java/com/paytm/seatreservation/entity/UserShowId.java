package com.paytm.seatreservation.entity;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserShowId implements Serializable {
    private UUID showId;
    private String userId;

    public UserShowId() {}

    public UserShowId(UUID showId, String userId) {
        this.showId = showId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserShowId that)) return false;
        return Objects.equals(showId, that.showId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() { return Objects.hash(showId, userId); }
}
