package com.taskqueue.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class PdfGenerationExecutor implements TaskExecutor {
    @Override
    public String getType() {
        return "PDF_GENERATION";
    }

    @Override
    public String execute(Map<String, Object> payload) throws Exception {
        String templateName = (String) payload.getOrDefault("templateName", "default_template");
        log.info("Generating PDF for template: {}", templateName);
        Thread.sleep(ThreadLocalRandom.current().nextInt(3000, 6000));
        return "PDF generated: " + templateName + ".pdf";
    }
}
