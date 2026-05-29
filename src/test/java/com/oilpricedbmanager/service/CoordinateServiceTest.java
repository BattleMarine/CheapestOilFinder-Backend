package com.oilpricedbmanager.service;

import com.oilpricedbmanager.domain.CoordinatePoint;
import com.oilpricedbmanager.domain.KatecPoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinateServiceTest {
    private final CoordinateService coordinateService = new CoordinateService();

    @Test
    void convertsWgs84ToKatecAndBackNearOriginalPoint() {
        KatecPoint katec = coordinateService.toKatec(37.5665, 126.9780);
        CoordinatePoint point = coordinateService.toWgs84(katec.x(), katec.y());

        assertThat(point.lat()).isCloseTo(37.5665, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(point.lon()).isCloseTo(126.9780, org.assertj.core.data.Offset.offset(0.0001));
    }
}