package com.taskqueue.worker;

import com.taskqueue.executor.TaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class TaskExecutorRegistry {
    private final Map<String, TaskExecutor> executorMap = new HashMap<>();

    public TaskExecutorRegistry(List<TaskExecutor> executors) {
        for (TaskExecutor executor : executors) {
            executorMap.put(executor.getType(), executor);
        }
        log.info("Registered {} task executors: {}", executors.size(), executorMap.keySet());
    }

    public TaskExecutor getExecutor(String type) {
        TaskExecutor executor = executorMap.get(type);
        if (executor == null) {
            throw new IllegalArgumentException("No executor found for task type: " + type);
        }
        return executor;
    }

    public boolean hasExecutor(String type) {
        return executorMap.containsKey(type);
    }
}
