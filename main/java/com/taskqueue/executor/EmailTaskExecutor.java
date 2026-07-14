package com.taskqueue.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class EmailTaskExecutor implements TaskExecutor {
    @Override
    public String getType() {
        return "EMAIL";
    }

    @Override
    public String execute(Map<String, Object> payload) throws Exception {
        String to = (String) payload.getOrDefault("to", "unknown@example.com");
        String subject = (String) payload.getOrDefault("subject", "No Subject");
        log.info("Sending email to {}, subject: {}", to, subject);
        Thread.sleep(ThreadLocalRandom.current().nextInt(1000, 3000));
        return "Email sent successfully to " + to;
    }
}
