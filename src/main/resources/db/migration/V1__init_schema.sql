CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE gas_station (
    uni_id TEXT PRIMARY KEY,
    poll_div_cd TEXT,
    os_nm TEXT NOT NULL,
    phone TEXT,
    addr TEXT,
    lat DOUBLE PRECISION NOT NULL,
    lon DOUBLE PRECISION NOT NULL,
    geom geometry(Point, 4326) GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lon, lat), 4326)) STORED,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_gas_station_geom ON gas_station USING GIST (geom);

CREATE TABLE fuel (
    uni_id TEXT PRIMARY KEY REFERENCES gas_station(uni_id) ON DELETE CASCADE,
    gas_hign INTEGER,
    gas_low INTEGER,
    disl INTEGER,
    lpg INTEGER,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE discount (
    discount_id BIGSERIAL PRIMARY KEY,
    poll_div_cd TEXT,
    discount_name TEXT NOT NULL,
    discount_type TEXT NOT NULL,
    discount_value INTEGER NOT NULL,
    fuel_type TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at TIMESTAMP,
    ends_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_discount_lookup ON discount (poll_div_cd, fuel_type, is_active);

CREATE TABLE sync_log (
    sync_id BIGSERIAL PRIMARY KEY,
    sync_type TEXT NOT NULL,
    status TEXT NOT NULL,
    message TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP
);

INSERT INTO gas_station (uni_id, poll_div_cd, os_nm, phone, addr, lat, lon)
VALUES
    ('TEST-001', 'SKE', 'Test SK Station', '02-0000-0001', 'Sejong-daero 110, Jung-gu, Seoul', 37.566610, 126.978388),
    ('TEST-002', 'GSC', 'Test GS Caltex', '02-0000-0002', 'Sejong-daero 175, Jongno-gu, Seoul', 37.572950, 126.976900),
    ('TEST-003', 'HDO', 'Test Hyundai Oilbank', '02-0000-0003', 'Eulji-ro 100, Jung-gu, Seoul', 37.566000, 126.991000);

INSERT INTO fuel (uni_id, gas_hign, gas_low, disl, lpg)
VALUES
    ('TEST-001', 1870, 1660, 1530, 990),
    ('TEST-002', 1850, 1645, 1525, 985),
    ('TEST-003', 1890, 1635, 1515, 1005);

INSERT INTO discount (poll_div_cd, discount_name, discount_type, discount_value, fuel_type)
VALUES
    ('SKE', 'Test SK per-liter discount', 'FIXED_PER_LITER', 30, NULL),
    ('GSC', 'Test GS fixed discount', 'FIXED_AMOUNT', 1000, 'REGULAR_GASOLINE');