package com.oilpricedbmanager.service;

import com.oilpricedbmanager.dto.AdminSyncLogClearResponse;
import com.oilpricedbmanager.dto.AdminSyncLogItem;
import com.oilpricedbmanager.repository.AdminSyncLogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AdminSyncLogMaintenanceService {
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdminSyncLogRepository adminSyncLogRepository;
    private final JdbcTemplate jdbcTemplate;

    public AdminSyncLogMaintenanceService(AdminSyncLogRepository adminSyncLogRepository, JdbcTemplate jdbcTemplate) {
        this.adminSyncLogRepository = adminSyncLogRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public AdminSyncLogClearResponse archiveAndClear() {
        List<AdminSyncLogItem> logs = adminSyncLogRepository.findRecent(null);
        LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
        String timestamp = now.format(FILE_TIMESTAMP);
        String csv = toCsv(logs, now);

        Path backupDir = Paths.get("logs");
        Path latestBackup = backupDir.resolve("log_history.csv");
        Path stampedBackup = backupDir.resolve("log_history_" + timestamp + ".csv");

        try {
            Files.createDirectories(backupDir);
            writeCsv(latestBackup, csv);
            writeCsv(stampedBackup, csv);
            jdbcTemplate.execute("TRUNCATE TABLE sector_sync_log, sync_log RESTART IDENTITY");
        } catch (IOException exception) {
            throw new IllegalStateException("로그 백업 파일을 저장하지 못했습니다.", exception);
        }

        int totalCount = logs.size();
        int batchCount = 0;
        int sectorCount = 0;
        for (AdminSyncLogItem log : logs) {
            if ("BATCH".equals(log.logType())) {
                batchCount++;
            } else if ("SECTOR".equals(log.logType())) {
                sectorCount++;
            }
        }

        return new AdminSyncLogClearResponse(
                totalCount,
                batchCount,
                sectorCount,
                latestBackup.toString().replace('\\', '/'),
                stampedBackup.toString().replace('\\', '/'),
                now.format(DISPLAY_TIMESTAMP)
        );
    }

    private static void writeCsv(Path path, String csv) throws IOException {
        Files.writeString(
                path,
                csv,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    private static String toCsv(List<AdminSyncLogItem> logs, LocalDateTime exportedAt) {
        StringBuilder builder = new StringBuilder();
        builder.append("exportedAt,").append(csv(exportedAt.format(DISPLAY_TIMESTAMP))).append('\n');
        builder.append("logType,refId,sectorId,sectorCode,syncType,fuelType,opinetProdcd,status,stationCount,detail,startedAt,finishedAt\n");
        for (AdminSyncLogItem log : logs) {
            builder.append(csv(log.logType())).append(',')
                    .append(log.refId()).append(',')
                    .append(csv(log.sectorId() == null ? null : String.valueOf(log.sectorId()))).append(',')
                    .append(csv(log.sectorCode())).append(',')
                    .append(csv(log.syncType())).append(',')
                    .append(csv(log.fuelType())).append(',')
                    .append(csv(log.opinetProdcd())).append(',')
                    .append(csv(log.status())).append(',')
                    .append(csv(log.stationCount() == null ? null : String.valueOf(log.stationCount()))).append(',')
                    .append(csv(log.detail())).append(',')
                    .append(csv(log.startedAt())).append(',')
                    .append(csv(log.finishedAt()))
                    .append('\n');
        }
        return builder.toString();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
