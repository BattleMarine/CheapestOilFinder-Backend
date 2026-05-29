CREATE TABLE sync_sector (
    sector_id BIGSERIAL PRIMARY KEY,
    sector_code TEXT UNIQUE NOT NULL,
    center_lat DOUBLE PRECISION NOT NULL,
    center_lon DOUBLE PRECISION NOT NULL,
    katec_x DOUBLE PRECISION NOT NULL,
    katec_y DOUBLE PRECISION NOT NULL,
    radius_m INTEGER NOT NULL DEFAULT 5000,
    geom geometry(Point, 4326) GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(center_lon, center_lat), 4326)) STORED,
    sync_tier TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority_score INTEGER NOT NULL DEFAULT 0,
    last_synced_at TIMESTAMP,
    next_sync_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    memo TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sync_sector_due ON sync_sector (enabled, next_sync_at, priority_score DESC, last_synced_at);
CREATE INDEX idx_sync_sector_geom ON sync_sector USING GIST (geom);
CREATE INDEX idx_sync_sector_tier ON sync_sector (sync_tier);

CREATE TABLE sector_sync_log (
    sync_log_id BIGSERIAL PRIMARY KEY,
    sector_id BIGINT NOT NULL REFERENCES sync_sector(sector_id) ON DELETE CASCADE,
    fuel_type TEXT NOT NULL,
    opinet_prodcd TEXT NOT NULL,
    status TEXT NOT NULL,
    station_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    error_message TEXT
);

CREATE INDEX idx_sector_sync_log_sector ON sector_sync_log (sector_id, started_at DESC);

INSERT INTO sync_sector (sector_code, center_lat, center_lon, katec_x, katec_y, radius_m, sync_tier, enabled, priority_score, memo)
VALUES
    ('SEOUL-CITYHALL-HOT', 37.5665, 126.9780, 314680.0, 544840.0, 5000, 'HOT', true, 1000, 'Initial Seoul city hall HOT test sector'),
    ('INCHEON-CITYHALL-HOT', 37.4563, 126.7052, 290420.0, 532790.0, 5000, 'HOT', true, 900, 'Initial Incheon HOT test sector'),
    ('SUWON-CITYHALL-HOT', 37.2636, 127.0286, 319240.0, 511300.0, 5000, 'HOT', true, 900, 'Initial Suwon HOT test sector');