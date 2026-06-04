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

        assertThat(point.lat()).isCloseTo(37.5665, org.assertj.core.data.Offset.offset(0.00001));
        assertThat(point.lon()).isCloseTo(126.9780, org.assertj.core.data.Offset.offset(0.00001));
    }

    @Test
    void appliesDatumShiftComparedToLegacyProjectionOnlyFormula() {
        CoordinatePoint corrected = coordinateService.toWgs84(314_681.8, 544_837.0);
        CoordinatePoint legacy = legacyProjectionOnlyToWgs84(314_681.8, 544_837.0);

        double distanceMeters = distanceMeters(corrected.lat(), corrected.lon(), legacy.lat(), legacy.lon());

        assertThat(distanceMeters).isGreaterThan(100.0);
        assertThat(corrected.lat()).isBetween(33.0, 39.5);
        assertThat(corrected.lon()).isBetween(124.0, 132.0);
    }

    private CoordinatePoint legacyProjectionOnlyToWgs84(double x, double y) {
        double a = 6377397.155;
        double f = 1.0 / 299.1528128;
        double lat0 = Math.toRadians(38.0);
        double lon0 = Math.toRadians(128.0);
        double k0 = 0.9999;
        double falseEasting = 400000.0;
        double falseNorthing = 600000.0;
        double e2 = 2 * f - f * f;
        double ep2 = e2 / (1 - e2);
        double m0 = meridionalArc(a, e2, lat0);

        double m = m0 + (y - falseNorthing) / k0;
        double mu = m / (a * (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * Math.pow(e2, 3) / 256));
        double e1 = (1 - Math.sqrt(1 - e2)) / (1 + Math.sqrt(1 - e2));

        double phi1 = mu
                + (3 * e1 / 2 - 27 * Math.pow(e1, 3) / 32) * Math.sin(2 * mu)
                + (21 * e1 * e1 / 16 - 55 * Math.pow(e1, 4) / 32) * Math.sin(4 * mu)
                + (151 * Math.pow(e1, 3) / 96) * Math.sin(6 * mu)
                + (1097 * Math.pow(e1, 4) / 512) * Math.sin(8 * mu);

        double sinPhi1 = Math.sin(phi1);
        double cosPhi1 = Math.cos(phi1);
        double tanPhi1 = Math.tan(phi1);
        double n1 = a / Math.sqrt(1 - e2 * sinPhi1 * sinPhi1);
        double r1 = a * (1 - e2) / Math.pow(1 - e2 * sinPhi1 * sinPhi1, 1.5);
        double t1 = tanPhi1 * tanPhi1;
        double c1 = ep2 * cosPhi1 * cosPhi1;
        double d = (x - falseEasting) / (n1 * k0);

        double phi = phi1 - (n1 * tanPhi1 / r1) * (d * d / 2
                - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * ep2) * Math.pow(d, 4) / 24
                + (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * ep2 - 3 * c1 * c1) * Math.pow(d, 6) / 720);
        double lambda = lon0 + (d
                - (1 + 2 * t1 + c1) * Math.pow(d, 3) / 6
                + (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * ep2 + 24 * t1 * t1) * Math.pow(d, 5) / 120) / cosPhi1;

        return new CoordinatePoint(Math.toDegrees(phi), Math.toDegrees(lambda));
    }

    private static double meridionalArc(double a, double e2, double phi) {
        return a * ((1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * Math.pow(e2, 3) / 256) * phi
                - (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * Math.pow(e2, 3) / 1024) * Math.sin(2 * phi)
                + (15 * e2 * e2 / 256 + 45 * Math.pow(e2, 3) / 1024) * Math.sin(4 * phi)
                - (35 * Math.pow(e2, 3) / 3072) * Math.sin(6 * phi));
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371008.8;
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);
        double sinHalfLat = Math.sin(dPhi / 2);
        double sinHalfLon = Math.sin(dLambda / 2);
        double a = sinHalfLat * sinHalfLat
                + Math.cos(phi1) * Math.cos(phi2) * sinHalfLon * sinHalfLon;
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }
}
