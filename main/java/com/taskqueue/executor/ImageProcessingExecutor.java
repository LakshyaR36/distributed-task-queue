package com.taskqueue.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class ImageProcessingExecutor implements TaskExecutor {
    @Override
    public String getType() {
        return "IMAGE_PROCESSING";
    }

    @Override
    public String execute(Map<String, Object> payload) throws Exception {
        String imageUrl = (String) payload.getOrDefault("imageUrl", "unknown.jpg");
        String operation = (String) payload.getOrDefault("operation", "resize");
        log.info("Processing image {}, operation: {}", imageUrl, operation);
        Thread.sleep(ThreadLocalRandom.current().nextInt(2000, 5000));
        return "Image processed: " + operation + " applied to " + imageUrl;
    }
}
