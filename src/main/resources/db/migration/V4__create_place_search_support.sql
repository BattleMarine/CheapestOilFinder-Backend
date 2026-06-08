CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS place_autocomplete_entry (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL,
    source_ref VARCHAR(128) NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    display_text VARCHAR(256) NOT NULL,
    normalized_text VARCHAR(256) NOT NULL,
    primary_text VARCHAR(256),
    secondary_text VARCHAR(256),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    search_weight INTEGER NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_place_autocomplete_entry UNIQUE (source_type, source_ref, entry_type)
);

CREATE INDEX IF NOT EXISTS idx_place_autocomplete_entry_normalized_text
    ON place_autocomplete_entry USING GIN (normalized_text gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_place_autocomplete_entry_enabled_weight
    ON place_autocomplete_entry (enabled, search_weight DESC, display_text);

CREATE TABLE IF NOT EXISTS place_search_cache (
    cache_key CHAR(64) PRIMARY KEY,
    search_mode VARCHAR(16) NOT NULL,
    query VARCHAR(512) NOT NULL,
    normalized_query VARCHAR(512) NOT NULL,
    page_no INTEGER NOT NULL,
    size_no INTEGER NOT NULL,
    sort_order VARCHAR(32) NOT NULL,
    center_latitude DOUBLE PRECISION,
    center_longitude DOUBLE PRECISION,
    radius_meters INTEGER,
    request_json TEXT NOT NULL,
    response_json TEXT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    status_code INTEGER NOT NULL,
    fetched_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    last_hit_at TIMESTAMP,
    hit_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_place_search_cache_expires_at
    ON place_search_cache (expires_at);

CREATE INDEX IF NOT EXISTS idx_place_search_cache_lookup
    ON place_search_cache (search_mode, normalized_query, page_no, size_no, sort_order);
