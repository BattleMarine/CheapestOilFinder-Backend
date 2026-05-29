ALTER TABLE sync_sector
    ADD COLUMN IF NOT EXISTS sector_shape geometry(Polygon, 4326),
    ADD COLUMN IF NOT EXISTS side_length_m INTEGER NOT NULL DEFAULT 5000,
    ADD COLUMN IF NOT EXISTS sector_source TEXT NOT NULL DEFAULT 'MANUAL';

CREATE INDEX IF NOT EXISTS idx_sync_sector_shape ON sync_sector USING GIST (sector_shape);
CREATE INDEX IF NOT EXISTS idx_sync_sector_source ON sync_sector (sector_source);
