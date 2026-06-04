package com.oilpricedbmanager.service;

import com.oilpricedbmanager.domain.CoordinatePoint;
import com.oilpricedbmanager.domain.KatecPoint;
import org.springframework.stereotype.Service;

@Service
public class CoordinateService {
    private static final double A = 6377397.155;
    private static final double F = 1.0 / 299.1528128;
    private static final double WGS84_A = 6378137.0;
    private static final double WGS84_F = 1.0 / 298.257223563;
    private static final double LAT0 = Math.toRadians(38.0);
    private static final double LON0 = Math.toRadians(128.0);
    private static final double K0 = 0.9999;
    private static final double FALSE_EASTING = 400000.0;
    private static final double FALSE_NORTHING = 600000.0;
    private static final double E2 = 2 * F - F * F;
    private static final double EP2 = E2 / (1 - E2);
    private static final double WGS84_E2 = 2 * WGS84_F - WGS84_F * WGS84_F;
    private static final double M0 = meridionalArc(LAT0);

    // Korea TM(KATEC) datum shift to/from WGS84.
    // Position-vector convention.
    private static final double DATUM_DX = -115.80;
    private static final double DATUM_DY = 474.99;
    private static final double DATUM_DZ = 674.11;
    private static final double DATUM_RX = Math.toRadians(1.16 / 3600.0);
    private static final double DATUM_RY = Math.toRadians(-2.31 / 3600.0);
    private static final double DATUM_RZ = Math.toRadians(-1.63 / 3600.0);
    private static final double DATUM_DS = 6.43e-6;

    public KatecPoint toKatec(double lat, double lon) {
        EcefPoint wgs84Ecef = geodeticToEcef(Math.toRadians(lat), Math.toRadians(lon), WGS84_A, WGS84_E2);
        EcefPoint besselEcef = inverseHelmert(wgs84Ecef);
        GeodeticPoint bessel = ecefToGeodetic(besselEcef, A, E2);

        double phi = bessel.latRadians();
        double lambda = bessel.lonRadians();
        double sinPhi = Math.sin(phi);
        double cosPhi = Math.cos(phi);
        double tanPhi = Math.tan(phi);

        double n = A / Math.sqrt(1 - E2 * sinPhi * sinPhi);
        double t = tanPhi * tanPhi;
        double c = EP2 * cosPhi * cosPhi;
        double a = (lambda - LON0) * cosPhi;
        double m = meridionalArc(phi);

        double x = FALSE_EASTING + K0 * n * (a
                + (1 - t + c) * Math.pow(a, 3) / 6
                + (5 - 18 * t + t * t + 72 * c - 58 * EP2) * Math.pow(a, 5) / 120);
        double y = FALSE_NORTHING + K0 * ((m - M0) + n * tanPhi * (a * a / 2
                + (5 - t + 9 * c + 4 * c * c) * Math.pow(a, 4) / 24
                + (61 - 58 * t + t * t + 600 * c - 330 * EP2) * Math.pow(a, 6) / 720));
        return new KatecPoint(x, y);
    }

    public CoordinatePoint toWgs84(double x, double y) {
        GeodeticPoint bessel = inverseProject(x, y);
        EcefPoint besselEcef = geodeticToEcef(bessel.latRadians(), bessel.lonRadians(), A, E2);
        EcefPoint wgs84Ecef = forwardHelmert(besselEcef);
        GeodeticPoint wgs84 = ecefToGeodetic(wgs84Ecef, WGS84_A, WGS84_E2);
        return new CoordinatePoint(Math.toDegrees(wgs84.latRadians()), Math.toDegrees(wgs84.lonRadians()));
    }

    private GeodeticPoint inverseProject(double x, double y) {
        double m = M0 + (y - FALSE_NORTHING) / K0;
        double mu = m / (A * (1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * Math.pow(E2, 3) / 256));
        double e1 = (1 - Math.sqrt(1 - E2)) / (1 + Math.sqrt(1 - E2));

        double phi1 = mu
                + (3 * e1 / 2 - 27 * Math.pow(e1, 3) / 32) * Math.sin(2 * mu)
                + (21 * e1 * e1 / 16 - 55 * Math.pow(e1, 4) / 32) * Math.sin(4 * mu)
                + (151 * Math.pow(e1, 3) / 96) * Math.sin(6 * mu)
                + (1097 * Math.pow(e1, 4) / 512) * Math.sin(8 * mu);

        double sinPhi1 = Math.sin(phi1);
        double cosPhi1 = Math.cos(phi1);
        double tanPhi1 = Math.tan(phi1);
        double n1 = A / Math.sqrt(1 - E2 * sinPhi1 * sinPhi1);
        double r1 = A * (1 - E2) / Math.pow(1 - E2 * sinPhi1 * sinPhi1, 1.5);
        double t1 = tanPhi1 * tanPhi1;
        double c1 = EP2 * cosPhi1 * cosPhi1;
        double d = (x - FALSE_EASTING) / (n1 * K0);

        double phi = phi1 - (n1 * tanPhi1 / r1) * (d * d / 2
                - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * EP2) * Math.pow(d, 4) / 24
                + (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * EP2 - 3 * c1 * c1) * Math.pow(d, 6) / 720);
        double lambda = LON0 + (d
                - (1 + 2 * t1 + c1) * Math.pow(d, 3) / 6
                + (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * EP2 + 24 * t1 * t1) * Math.pow(d, 5) / 120) / cosPhi1;

        return new GeodeticPoint(phi, lambda);
    }

    private static double meridionalArc(double phi) {
        return A * ((1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * Math.pow(E2, 3) / 256) * phi
                - (3 * E2 / 8 + 3 * E2 * E2 / 32 + 45 * Math.pow(E2, 3) / 1024) * Math.sin(2 * phi)
                + (15 * E2 * E2 / 256 + 45 * Math.pow(E2, 3) / 1024) * Math.sin(4 * phi)
                - (35 * Math.pow(E2, 3) / 3072) * Math.sin(6 * phi));
    }

    private static EcefPoint geodeticToEcef(double latRadians, double lonRadians, double a, double e2) {
        double sinLat = Math.sin(latRadians);
        double cosLat = Math.cos(latRadians);
        double sinLon = Math.sin(lonRadians);
        double cosLon = Math.cos(lonRadians);
        double n = a / Math.sqrt(1 - e2 * sinLat * sinLat);
        double x = n * cosLat * cosLon;
        double y = n * cosLat * sinLon;
        double z = n * (1 - e2) * sinLat;
        return new EcefPoint(x, y, z);
    }

    private static GeodeticPoint ecefToGeodetic(EcefPoint ecef, double a, double e2) {
        double x = ecef.x();
        double y = ecef.y();
        double z = ecef.z();
        double lon = Math.atan2(y, x);
        double p = Math.sqrt(x * x + y * y);
        double lat = Math.atan2(z, p * (1 - e2));

        for (int i = 0; i < 8; i++) {
            double sinLat = Math.sin(lat);
            double n = a / Math.sqrt(1 - e2 * sinLat * sinLat);
            double h = p / Math.cos(lat) - n;
            double nextLat = Math.atan2(z, p * (1 - e2 * n / (n + h)));
            if (Math.abs(nextLat - lat) < 1e-14) {
                lat = nextLat;
                break;
            }
            lat = nextLat;
        }

        return new GeodeticPoint(lat, lon);
    }

    private static EcefPoint forwardHelmert(EcefPoint source) {
        double x = source.x();
        double y = source.y();
        double z = source.z();
        double scale = 1 + DATUM_DS;
        double tx = DATUM_DX + scale * (x - DATUM_RZ * y + DATUM_RY * z);
        double ty = DATUM_DY + scale * (DATUM_RZ * x + y - DATUM_RX * z);
        double tz = DATUM_DZ + scale * (-DATUM_RY * x + DATUM_RX * y + z);
        return new EcefPoint(tx, ty, tz);
    }

    private static EcefPoint inverseHelmert(EcefPoint source) {
        double x = source.x();
        double y = source.y();
        double z = source.z();
        double scale = 1 - DATUM_DS;
        double tx = -DATUM_DX + scale * (x + DATUM_RZ * y - DATUM_RY * z);
        double ty = -DATUM_DY + scale * (-DATUM_RZ * x + y + DATUM_RX * z);
        double tz = -DATUM_DZ + scale * (DATUM_RY * x - DATUM_RX * y + z);
        return new EcefPoint(tx, ty, tz);
    }

    private record EcefPoint(double x, double y, double z) {
    }

    private record GeodeticPoint(double latRadians, double lonRadians) {
    }
}
