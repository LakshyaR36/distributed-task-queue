package com.taskqueue.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskqueue.queue.DeadLetterQueueService;
import com.taskqueue.queue.RedisQueueService;
import com.taskqueue.repository.TaskRepository;
import com.taskqueue.service.WorkerService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class WorkerManager {
    private final RedisQueueService queueService;
    private final TaskRepository taskRepository;
    private final TaskExecutorRegistry executorRegistry;
    private final WorkerService workerService;
    private final StringRedisTemplate redisTemplate;
    private final DeadLetterQueueService deadLetterQueueService;
    private final ObjectMapper objectMapper;

    @Value("${app.worker.count:4}")
    private int workerCount;

    @Value("${app.worker.heartbeat-interval:5000}")
    private int heartbeatInterval;

    @Value("${app.retry.base-delay:1000}")
    private int baseRetryDelay;

    @Value("${app.retry.max-delay:60000}")
    private int maxRetryDelay;

    private final List<Thread> workerThreads = new ArrayList<>();
    private final List<WorkerRunner> workerRunners = new ArrayList<>();

    @EventListener(ApplicationReadyEvent.class)
    public void startWorkers() {
        String hostname = "unknown";
        try {
            hostname = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            log.warn("Could not determine hostname");
        }

        for (int i = 0; i < workerCount; i++) {
            String workerId = "worker-" + hostname + "-" + i;
            WorkerRunner runner = new WorkerRunner(
                    workerId, queueService, taskRepository, executorRegistry,
                    workerService, redisTemplate, deadLetterQueueService,
                    objectMapper, baseRetryDelay, maxRetryDelay
            );
            Thread thread = new Thread(runner);
            thread.setDaemon(true);
            thread.setName("Thread-" + workerId);
            thread.start();
            workerRunners.add(runner);
            workerThreads.add(thread);
        }
        log.info("Started {} workers", workerCount);
    }

    @PreDestroy
    public void stopWorkers() {
        log.info("Shutting down workers...");
        for (WorkerRunner runner : workerRunners) {
            runner.stop();
        }
        for (Thread thread : workerThreads) {
            thread.interrupt();
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Shutdown complete");
    }
}
