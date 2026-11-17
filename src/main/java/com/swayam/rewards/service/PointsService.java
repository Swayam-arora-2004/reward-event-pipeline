package com.swayam.rewards.service;

import com.swayam.rewards.config.RewardsProperties;
import com.swayam.rewards.messaging.PurchaseEvent;
import com.swayam.rewards.repository.MemberPointsRepository;
import com.swayam.rewards.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointsService {

    private static final Logger log = LoggerFactory.getLogger(PointsService.class);

    private final ProcessedEventRepository processedEvents;
    private final MemberPointsRepository memberPoints;
    private final long rupeesPerPoint;

    public PointsService(ProcessedEventRepository processedEvents, MemberPointsRepository memberPoints,
            RewardsProperties properties) {
        this.processedEvents = processedEvents;
        this.memberPoints = memberPoints;
        this.rupeesPerPoint = properties.points().rupeesPerPoint();
    }

    /** The points rule: floor(amount / rupeesPerPoint). 99 -> 9, 100 -> 10, 5 -> 0 at 10 rupees per point. */
    public static long pointsFor(long amountRupees, long rupeesPerPoint) {
        return amountRupees / rupeesPerPoint;
    }

    /**
     * Credits points once per eventId. Both writes share ONE transaction: either the event is
     * recorded AND the balance rises, or neither happens (D-004).
     *
     * @return true if points were credited, false if the event was a duplicate
     */
    @Transactional
    public boolean credit(PurchaseEvent event) {
        long points = pointsFor(event.amountRupees(), rupeesPerPoint);

        int inserted = processedEvents.insertIfAbsent(event.eventId(), event.memberId(), points);
        if (inserted == 0) {
            log.info("Duplicate event ignored (eventId={})", event.eventId());
            return false;
        }

        memberPoints.addPoints(event.memberId(), points);
        log.info("Credited points (eventId={}, memberId={}, points={})", event.eventId(), event.memberId(), points);
        return true;
    }
}
