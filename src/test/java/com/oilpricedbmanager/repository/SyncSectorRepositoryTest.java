package com.oilpricedbmanager.repository;

import com.oilpricedbmanager.domain.SyncTier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncSectorRepositoryTest {
    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final SyncSectorRepository repository = new SyncSectorRepository(jdbcTemplate);

    @SuppressWarnings("unchecked")
    @Test
    void findAutoDueSectors_uses_only_hot_and_warm_sectors() {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.findAutoDueSectors(10);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(sqlCaptor.getValue()).contains("sync_tier IN ('HOT', 'WARM')");
        assertThat(sqlCaptor.getValue()).contains("next_sync_at <= CURRENT_TIMESTAMP");
    }

    @SuppressWarnings("unchecked")
    @Test
    void findEnabledSectorsByTiers_filters_requested_tiers() {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.findEnabledSectorsByTiers(List.of(SyncTier.HOT, SyncTier.WARM));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(sqlCaptor.getValue()).contains("sync_tier IN (:tiers)");
        assertThat(sqlCaptor.getValue()).contains("WHERE enabled = true");
    }

    @Test
    void markSynced_sets_daily_delay_for_hot() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.markSynced(11L, SyncTier.HOT);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), paramsCaptor.capture());

        assertThat(paramsCaptor.getValue().getValue("minutes")).isEqualTo(1440);
    }

    @Test
    void markSynced_sets_three_day_delay_for_warm() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.markSynced(11L, SyncTier.WARM);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), paramsCaptor.capture());

        assertThat(paramsCaptor.getValue().getValue("minutes")).isEqualTo(4320);
    }

    @Test
    void markSynced_keeps_cold_manual_only_delay() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.markSynced(11L, SyncTier.COLD);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), paramsCaptor.capture());

        assertThat(paramsCaptor.getValue().getValue("minutes")).isEqualTo(525600);
    }
}
