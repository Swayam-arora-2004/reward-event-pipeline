package com.swayam.rewards.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A member's running point balance (read side; writes use an atomic upsert, see MemberPointsRepository). */
@Entity
@Table(name = "member_points")
public class MemberPoints {

    @Id
    @Column(name = "member_id")
    private String memberId;

    @Column(name = "balance", nullable = false)
    private long balance;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MemberPoints() {
        // Required by JPA.
    }

    public String getMemberId() {
        return memberId;
    }

    public long getBalance() {
        return balance;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
