package com.dev.gateway.filter.access.task;

import com.dev.gateway.filter.access.service.AccessRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 访问记录清理定时任务
 * 定期清理过期的访问记录
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "access-record.enabled", havingValue = "true", matchIfMissing = true)
public class AccessRecordCleanupTask {

    private final AccessRecordService accessRecordService;

    @Value("${access-record.retention-days:30}")
    private int retentionDays;

    public AccessRecordCleanupTask(AccessRecordService accessRecordService) {
        this.accessRecordService = accessRecordService;
    }

    /**
     * 每天凌晨2点执行清理任务
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldAccessRecords() {
        log.info("开始执行访问记录清理任务，保留天数: {}", retentionDays);
        
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
        
        accessRecordService.cleanupOldRecords(cutoffTime)
                .subscribe(
                    deletedCount -> {
                        log.info("访问记录清理任务完成，删除了 {} 条过期记录", deletedCount);
                    },
                    error -> {
                        log.error("访问记录清理任务失败: {}", error.getMessage(), error);
                    }
                );
    }

    /**
     * 每小时执行一次小规模清理（清理超过保留期限的记录）
     */
    @Scheduled(fixedRate = 3600000) // 1小时 = 3600000毫秒
    public void hourlyCleanup() {
        if (retentionDays > 0) {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays + 1);
            
            accessRecordService.cleanupOldRecords(cutoffTime)
                    .subscribe(
                        deletedCount -> {
                            if (deletedCount > 0) {
                                log.debug("小时清理任务完成，删除了 {} 条过期记录", deletedCount);
                            }
                        },
                        error -> {
                            log.warn("小时清理任务失败: {}", error.getMessage());
                        }
                    );
        }
    }
} 