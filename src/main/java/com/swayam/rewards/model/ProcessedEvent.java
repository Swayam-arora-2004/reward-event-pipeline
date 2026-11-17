package com.swayam.rewards.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One row per credited event. The primary key on event_id is the duplicate guard. */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "member_id", nullable = false)
    private String memberId;

    @Column(name = "points", nullable = false)
    private long points;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // Required by JPA.
    }

    public String getEventId() {
        return eventId;
    }

    public String getMemberId() {
        return memberId;
    }

    public long getPoints() {
        return points;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
