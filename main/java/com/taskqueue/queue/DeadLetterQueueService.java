package com.taskqueue.queue;

import com.taskqueue.entity.Task;
import com.taskqueue.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeadLetterQueueService {
    private final StringRedisTemplate redisTemplate;
    private final TaskRepository taskRepository;

    @Value("${app.queue.dlq-key:dtq:dlq}")
    private String dlqKey;

    @Transactional
    public void moveToDlq(UUID taskId) {
        redisTemplate.opsForList().leftPush(dlqKey, taskId.toString());
        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isPresent()) {
            Task task = taskOpt.get();
            task.setInDlq(true);
            taskRepository.save(task);
        }
        log.info("Moved task {} to DLQ", taskId);
    }

    public List<UUID> listDlq() {
        List<String> ids = redisTemplate.opsForList().range(dlqKey, 0, -1);
        if (ids == null) return List.of();
        return ids.stream().map(UUID::fromString).collect(Collectors.toList());
    }

    public void removeFromDlq(UUID taskId) {
        redisTemplate.opsForList().remove(dlqKey, 0, taskId.toString());
    }

    public long getDlqSize() {
        Long size = redisTemplate.opsForList().size(dlqKey);
        return size != null ? size : 0;
    }
}
