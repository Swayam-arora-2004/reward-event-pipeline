package com.swayam.rewards.repository;

import com.swayam.rewards.model.MemberPoints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberPointsRepository extends JpaRepository<MemberPoints, String> {

    /** Creates the member with this balance, or adds to the existing balance, in one atomic statement. */
    @Modifying
    @Query(value = "INSERT INTO member_points (member_id, balance) VALUES (:memberId, :points) "
            + "ON CONFLICT (member_id) DO UPDATE "
            + "SET balance = member_points.balance + EXCLUDED.balance, updated_at = now()", nativeQuery = true)
    int addPoints(@Param("memberId") String memberId, @Param("points") long points);
}
