package com.taskqueue.queue;

import com.taskqueue.entity.TaskPriority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisQueueService {
    private final StringRedisTemplate redisTemplate;
    
    @Value("${app.queue.prefix:dtq:queue}")
    private String queuePrefix;

    public void enqueue(UUID taskId, TaskPriority priority) {
        String key = getQueueKey(priority);
        redisTemplate.opsForList().leftPush(key, taskId.toString());
        log.info("Enqueued task {} to {}", taskId, key);
    }

    public Optional<UUID> dequeue() {
        TaskPriority[] order = {TaskPriority.CRITICAL, TaskPriority.HIGH, TaskPriority.NORMAL, TaskPriority.LOW};
        for (TaskPriority priority : order) {
            String key = getQueueKey(priority);
            String taskId = redisTemplate.opsForList().rightPop(key);
            if (taskId != null) {
                return Optional.of(UUID.fromString(taskId));
            }
        }
        return Optional.empty();
    }

    public void removeFromQueue(UUID taskId) {
        for (TaskPriority priority : TaskPriority.values()) {
            String key = getQueueKey(priority);
            redisTemplate.opsForList().remove(key, 0, taskId.toString());
        }
        log.info("Removed task {} from all queues", taskId);
    }

    public long getQueueSize(TaskPriority priority) {
        Long size = redisTemplate.opsForList().size(getQueueKey(priority));
        return size != null ? size : 0;
    }

    public Map<String, Long> getQueueSizes() {
        Map<String, Long> sizes = new HashMap<>();
        for (TaskPriority priority : TaskPriority.values()) {
            sizes.put(priority.name(), getQueueSize(priority));
        }
        return sizes;
    }

    private String getQueueKey(TaskPriority priority) {
        return queuePrefix + ":" + priority.name().toLowerCase();
    }
}
