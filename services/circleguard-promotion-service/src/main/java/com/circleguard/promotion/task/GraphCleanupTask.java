package com.circleguard.promotion.task;

import com.circleguard.promotion.repository.graph.UserNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class GraphCleanupTask {

    private final UserNodeRepository userNodeRepository;

    @Value("${GRAPH_RETENTION_DAYS:14}")
    private int GRAPH_RETENTION_DAYS;

    /**
     * Hourly task to purge proximity relationships older than 14 days.
     * Adheres to NFR-4 (Data Minimization) and ensures graph performance.
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour on the hour
    @Transactional("neo4jTransactionManager")
    public void purgeStaleEncounters() {
        long threshold = System.currentTimeMillis() - (GRAPH_RETENTION_DAYS * 24L * 60 * 60 * 1000);
        log.info("Starting automated graph cleanup for encounters older than {} days (Threshold: {})", GRAPH_RETENTION_DAYS, threshold);
        
        try {
            Long deletedCount = userNodeRepository.purgeStaleEncounters(threshold);
            log.info("Graph cleanup successful. Purged {} stale ENCOUNTERED relationships.", deletedCount != null ? deletedCount : 0);
        } catch (Exception e) {
            log.error("Failed to execute graph cleanup task", e);
        }
    }
}
