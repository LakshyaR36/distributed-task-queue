package com.taskqueue.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class ReportGenerationExecutor implements TaskExecutor {
    @Override
    public String getType() {
        return "REPORT_GENERATION";
    }

    @Override
    public String execute(Map<String, Object> payload) throws Exception {
        String reportType = (String) payload.getOrDefault("reportType", "daily_summary");
        log.info("Generating report type: {}", reportType);
        Thread.sleep(ThreadLocalRandom.current().nextInt(4000, 8000));
        return "Report generated: " + reportType;
    }
}
