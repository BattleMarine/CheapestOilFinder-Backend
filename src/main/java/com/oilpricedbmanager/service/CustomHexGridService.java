package com.oilpricedbmanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oilpricedbmanager.domain.CoordinatePoint;
import com.oilpricedbmanager.domain.GeneratedHexSector;
import com.oilpricedbmanager.domain.KatecPoint;
import com.oilpricedbmanager.domain.SyncTier;
import com.oilpricedbmanager.dto.HexGridGenerationResponse;
import com.oilpricedbmanager.repository.SyncSectorRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CustomHexGridService {
    private static final String KOREA_GEOJSON = "static/admin/south-korea.geojson";
    private static final String SECTOR_SOURCE = "CUSTOM_5KM_HEX";
    private static final int OPINET_RADIUS_METERS = 5000;

    private final ObjectMapper objectMapper;
    private final CoordinateService coordinateService;
    private final SyncSectorRepository syncSectorRepository;

    public CustomHexGridService(
            ObjectMapper objectMapper,
            CoordinateService coordinateService,
            SyncSectorRepository syncSectorRepository
    ) {
        this.objectMapper = objectMapper;
        this.coordinateService = coordinateService;
        this.syncSectorRepository = syncSectorRepository;
    }

    public HexGridGenerationResponse generateKoreaHexGrid(int requestedSideLengthMeters) {
        int sideLengthMeters = requestedSideLengthMeters <= 0 ? OPINET_RADIUS_METERS : requestedSideLengthMeters;
        if (sideLengthMeters < 1000 || sideLengthMeters > 20000) {
            throw new IllegalArgumentException("sideLengthMeters must be between 1000 and 20000");
        }

        List<Polygon> polygons = loadKoreaPolygons();
        Bounds bounds = Bounds.from(polygons);
        List<GeneratedHexSector> sectors = generateSectors(polygons, bounds, sideLengthMeters);

        int upserted = 0;
        for (GeneratedHexSector sector : sectors) {
            upserted += syncSectorRepository.upsertGeneratedSector(sector);
        }
        return new HexGridGenerationResponse(sideLengthMeters, sectors.size(), upserted, SECTOR_SOURCE);
    }

    private List<GeneratedHexSector> generateSectors(List<Polygon> polygons, Bounds bounds, int sideLengthMeters) {
        double side = sideLengthMeters;
        double xSpacing = Math.sqrt(3.0) * side;
        double ySpacing = 1.5 * side;
        double margin = side * 2.0;
        List<GeneratedHexSector> sectors = new ArrayList<>();

        int row = 0;
        for (double y = bounds.minY - margin; y <= bounds.maxY + margin; y += ySpacing, row++) {
            double xOffset = (row % 2 == 0) ? 0.0 : xSpacing / 2.0;
            int col = 0;
            for (double x = bounds.minX - margin + xOffset; x <= bounds.maxX + margin; x += xSpacing, col++) {
                if (!contains(polygons, x, y)) {
                    continue;
                }
                CoordinatePoint center = coordinateService.toWgs84(x, y);
                List<CoordinatePoint> boundary = hexBoundary(x, y, side);
                String sectorCode = String.format(Locale.US, "HEX5K-R%04d-C%04d", row, col);
                SyncTier tier = classifyTier(center.lat(), center.lon());
                int priority = priorityScore(tier);
                String memo = "Generated 5km custom hex sector for Opinet aroundAll center calls";
                sectors.add(new GeneratedHexSector(
                        sectorCode,
                        center.lat(),
                        center.lon(),
                        x,
                        y,
                        OPINET_RADIUS_METERS,
                        sideLengthMeters,
                        SECTOR_SOURCE,
                        toPolygonWkt(boundary),
                        tier,
                        true,
                        priority,
                        memo
                ));
            }
        }
        return sectors;
    }

    private List<CoordinatePoint> hexBoundary(double centerX, double centerY, double sideLengthMeters) {
        List<CoordinatePoint> points = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(-90 + 60 * i);
            double x = centerX + sideLengthMeters * Math.cos(angle);
            double y = centerY + sideLengthMeters * Math.sin(angle);
            points.add(coordinateService.toWgs84(x, y));
        }
        return points;
    }

    private static String toPolygonWkt(List<CoordinatePoint> boundary) {
        StringBuilder builder = new StringBuilder("POLYGON((");
        for (int i = 0; i < boundary.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            CoordinatePoint point = boundary.get(i);
            builder.append(String.format(Locale.US, "%.8f %.8f", point.lon(), point.lat()));
        }
        CoordinatePoint first = boundary.getFirst();
        builder.append(',').append(String.format(Locale.US, "%.8f %.8f", first.lon(), first.lat()));
        builder.append("))");
        return builder.toString();
    }

    private SyncTier classifyTier(double lat, double lon) {
        if (inBox(lat, lon, 36.80, 38.35, 126.00, 127.90)) {
            return SyncTier.HOT;
        }
        if (inBox(lat, lon, 35.00, 35.40, 128.75, 129.35)
                || inBox(lat, lon, 35.35, 35.75, 128.95, 129.55)
                || inBox(lat, lon, 35.65, 36.10, 128.30, 128.90)
                || inBox(lat, lon, 35.00, 35.35, 126.65, 127.05)
                || inBox(lat, lon, 35.95, 36.65, 127.10, 127.70)) {
            return SyncTier.WARM;
        }
        return SyncTier.COLD;
    }

    private static int priorityScore(SyncTier tier) {
        return switch (tier) {
            case HOT -> 1000;
            case WARM -> 600;
            case COLD -> 100;
            case DISABLED -> 0;
        };
    }

    private static boolean inBox(double lat, double lon, double minLat, double maxLat, double minLon, double maxLon) {
        return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
    }

    private List<Polygon> loadKoreaPolygons() {
        try (InputStream inputStream = new ClassPathResource(KOREA_GEOJSON).getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            List<Polygon> polygons = new ArrayList<>();
            for (JsonNode feature : root.path("features")) {
                JsonNode geometry = feature.path("geometry");
                String type = geometry.path("type").asText();
                JsonNode coordinates = geometry.path("coordinates");
                if ("Polygon".equals(type)) {
                    polygons.add(toPolygon(coordinates));
                } else if ("MultiPolygon".equals(type)) {
                    for (JsonNode polygonNode : coordinates) {
                        polygons.add(toPolygon(polygonNode));
                    }
                }
            }
            return polygons;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load South Korea GeoJSON", e);
        }
    }

    private Polygon toPolygon(JsonNode polygonNode) {
        List<Ring> rings = new ArrayList<>();
        for (JsonNode ringNode : polygonNode) {
            List<KatecPoint> points = new ArrayList<>();
            for (JsonNode coord : ringNode) {
                double lon = coord.get(0).asDouble();
                double lat = coord.get(1).asDouble();
                points.add(coordinateService.toKatec(lat, lon));
            }
            rings.add(new Ring(points));
        }
        return new Polygon(rings);
    }

    private static boolean contains(List<Polygon> polygons, double x, double y) {
        for (Polygon polygon : polygons) {
            if (polygon.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    private record Ring(List<KatecPoint> points) {
        boolean contains(double x, double y) {
            boolean inside = false;
            int j = points.size() - 1;
            for (int i = 0; i < points.size(); i++) {
                KatecPoint pi = points.get(i);
                KatecPoint pj = points.get(j);
                boolean intersects = ((pi.y() > y) != (pj.y() > y))
                        && (x < (pj.x() - pi.x()) * (y - pi.y()) / (pj.y() - pi.y()) + pi.x());
                if (intersects) {
                    inside = !inside;
                }
                j = i;
            }
            return inside;
        }
    }

    private record Polygon(List<Ring> rings) {
        boolean contains(double x, double y) {
            if (rings.isEmpty() || !rings.getFirst().contains(x, y)) {
                return false;
            }
            for (int i = 1; i < rings.size(); i++) {
                if (rings.get(i).contains(x, y)) {
                    return false;
                }
            }
            return true;
        }
    }

    private record Bounds(double minX, double minY, double maxX, double maxY) {
        static Bounds from(List<Polygon> polygons) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (Polygon polygon : polygons) {
                for (Ring ring : polygon.rings()) {
                    for (KatecPoint point : ring.points()) {
                        minX = Math.min(minX, point.x());
                        minY = Math.min(minY, point.y());
                        maxX = Math.max(maxX, point.x());
                        maxY = Math.max(maxY, point.y());
                    }
                }
            }
            return new Bounds(minX, minY, maxX, maxY);
        }
    }
}
