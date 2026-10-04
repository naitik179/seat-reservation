package com.paytm.seatreservation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.util.UUID;
// This is useful to check the max number of tickets which can be booked for a particular show by one user
@Entity
@Table(name = "user_show")
@IdClass(UserShowId.class)
public class UserShow {

    @Id
    @Column(name = "show_id")
    private UUID showId;

    @Id
    @Column(name = "user_id", length = 200)
    private String userId;

    @Column(name = "seat_count", nullable = false)
    private int seatCount;

    public UUID getShowId() { return showId; }
    public void setShowId(UUID showId) { this.showId = showId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public int getSeatCount() { return seatCount; }
    public void setSeatCount(int seatCount) { this.seatCount = seatCount; }
}
