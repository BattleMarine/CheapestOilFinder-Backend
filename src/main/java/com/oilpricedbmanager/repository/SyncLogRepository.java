package com.oilpricedbmanager.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SyncLogRepository {
    private final JdbcTemplate jdbcTemplate;

    public SyncLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long start(String syncType) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO sync_log (sync_type, status) VALUES (?, 'RUNNING') RETURNING sync_id",
                Long.class,
                syncType
        );
    }

    public void finish(long syncId, String status, String message) {
        jdbcTemplate.update(
                "UPDATE sync_log SET status = ?, message = ?, finished_at = CURRENT_TIMESTAMP WHERE sync_id = ?",
                status,
                message,
                syncId
        );
    }
}
