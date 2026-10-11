package com.aives.modules.grading.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Chay ngam: vai giay 1 lan chot cac luot thi qua han va goi AI cham cac luot thi vua xong.
 * Tat bang app.grading.auto-enabled = false (cac test tu dong tat de tu goi cham khi can).
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.grading.auto-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class GradingScheduler {

    // Moi lan quet cham toi da bay nhieu luot thi, de 1 lan quet khong chay qua lau
    private static final int MAX_ATTEMPTS_PER_RUN = 3;

    private final AttemptGradingService attemptGradingService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        int reset = attemptGradingService.resetInterruptedGrading();
        if (reset > 0) {
            log.info("{} luot thi dang cham do thi backend bi tat, se duoc cham lai", reset);
        }
    }

    // fixedDelay: lan quet sau chi bat dau khi lan truoc da xong, nen khong bao gio cham chong len nhau
    @Scheduled(fixedDelayString = "${app.grading.poll-interval-ms:5000}", initialDelayString = "${app.grading.poll-interval-ms:5000}")
    public void run() {
        try {
            attemptGradingService.closeOverdueAttempts();
            attemptGradingService.gradeWaitingAttempts(MAX_ATTEMPTS_PER_RUN);
        } catch (RuntimeException ex) {
            log.error("Lan quet cham diem bi loi", ex);
        }
    }
}
