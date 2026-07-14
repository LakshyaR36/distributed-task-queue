package com.taskqueue.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@Slf4j
public class DummySleepExecutor implements TaskExecutor {
    @Override
    public String getType() {
        return "DUMMY_SLEEP";
    }

    @Override
    public String execute(Map<String, Object> payload) throws Exception {
        int durationMs = 5000;
        if (payload.containsKey("durationMs")) {
            durationMs = Integer.parseInt(payload.get("durationMs").toString());
        }
        log.info("Sleeping for {} ms", durationMs);
        Thread.sleep(durationMs);
        return "Slept for " + durationMs + "ms";
    }
}
