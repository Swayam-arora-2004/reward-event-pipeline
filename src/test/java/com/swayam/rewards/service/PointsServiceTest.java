package com.swayam.rewards.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PointsServiceTest {

    private static final long RUPEES_PER_POINT = 10;

    @ParameterizedTest
    @CsvSource({"99, 9", "100, 10", "5, 0", "1, 0", "10, 1", "109, 10", "1000000000, 100000000"})
    void pointsFor_variousAmounts_returnsFlooredPoints(long amountRupees, long expectedPoints) {
        assertThat(PointsService.pointsFor(amountRupees, RUPEES_PER_POINT)).isEqualTo(expectedPoints);
    }

    @Test
    void pointsFor_differentRate_usesTheGivenRate() {
        assertThat(PointsService.pointsFor(99, 5)).isEqualTo(19);
    }
}
