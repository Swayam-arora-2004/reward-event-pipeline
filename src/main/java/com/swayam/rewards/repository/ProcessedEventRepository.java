package com.swayam.rewards.repository;

import com.swayam.rewards.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    /**
     * Inserts the event unless its id already exists. Returns 1 if inserted, 0 if it was a duplicate.
     * One atomic statement, so two consumers racing on the same eventId cannot both win (D-004).
     */
    @Modifying
    @Query(value = "INSERT INTO processed_event (event_id, member_id, points) "
            + "VALUES (:eventId, :memberId, :points) ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("memberId") String memberId,
            @Param("points") long points);
}
