package com.taskqueue.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskqueue.entity.Task;
import com.taskqueue.entity.TaskStatus;
import com.taskqueue.entity.WorkerStatus;
import com.taskqueue.executor.TaskExecutor;
import com.taskqueue.queue.DeadLetterQueueService;
import com.taskqueue.queue.RedisQueueService;
import com.taskqueue.repository.TaskRepository;
import com.taskqueue.service.WorkerService;
import com.taskqueue.util.RetryDelayCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class WorkerRunner implements Runnable {

    private volatile boolean running = true;
    private final String workerId;
    private final RedisQueueService queueService;
    private final TaskRepository taskRepository;
    private final TaskExecutorRegistry executorRegistry;
    private final WorkerService workerService;
    private final StringRedisTemplate redisTemplate;
    private final DeadLetterQueueService deadLetterQueueService;
    private final ObjectMapper objectMapper;
    private final int baseRetryDelay;
    private final int maxRetryDelay;

    public WorkerRunner(String workerId,
                        RedisQueueService queueService,
                        TaskRepository taskRepository,
                        TaskExecutorRegistry executorRegistry,
                        WorkerService workerService,
                        StringRedisTemplate redisTemplate,
                        DeadLetterQueueService deadLetterQueueService,
                        ObjectMapper objectMapper,
                        int baseRetryDelay,
                        int maxRetryDelay) {
        this.workerId = workerId;
        this.queueService = queueService;
        this.taskRepository = taskRepository;
        this.executorRegistry = executorRegistry;
        this.workerService = workerService;
        this.redisTemplate = redisTemplate;
        this.deadLetterQueueService = deadLetterQueueService;
        this.objectMapper = objectMapper;
        this.baseRetryDelay = baseRetryDelay;
        this.maxRetryDelay = maxRetryDelay;
    }

    @Override
    public void run() {
        log.info("Worker {} started", workerId);
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            workerService.registerWorker(workerId, hostname);
        } catch (Exception e) {
            workerService.registerWorker(workerId, "unknown-host");
        }

        long lastHeartbeat = System.currentTimeMillis();

        while (running) {
            try {
                if (System.currentTimeMillis() - lastHeartbeat > 5000) {
                    workerService.updateHeartbeat(workerId);
                    lastHeartbeat = System.currentTimeMillis();
                }

                Optional<UUID> taskIdOpt = queueService.dequeue();
                if (taskIdOpt.isEmpty()) {
                    workerService.updateWorkerStatus(workerId, WorkerStatus.IDLE, null);
                    Thread.sleep(500);
                    continue;
                }

                UUID taskId = taskIdOpt.get();
                String lockKey = "dtq:lock:" + taskId;
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, workerId, Duration.ofMinutes(5));
                if (acquired == null || !acquired) {
                    log.warn("Could not acquire lock for task {}, skipping", taskId);
                    continue;
                }

                try {
                    Optional<Task> taskOpt = taskRepository.findById(taskId);
                    if (taskOpt.isEmpty()) {
                        log.warn("Task {} not found in DB, skipping", taskId);
                        continue;
                    }

                    Task task = taskOpt.get();

                    if (task.getStatus() == TaskStatus.CANCELLED) {
                        log.info("Task {} is cancelled, skipping", taskId);
                        continue;
                    }

                    task.setStatus(TaskStatus.RUNNING);
                    task.setWorkerId(workerId);
                    task.setStartedAt(LocalDateTime.now());
                    taskRepository.save(task);
                    workerService.updateWorkerStatus(workerId, WorkerStatus.BUSY, taskId);

                    TaskExecutor executor = executorRegistry.getExecutor(task.getTaskType());
                    org.slf4j.MDC.put("taskId", taskId.toString());
                    org.slf4j.MDC.put("workerId", workerId);
                    
                    try {
                        log.info("Worker {} executing task {} (type: {})", workerId, taskId, task.getTaskType());

                        Map<String, Object> payload = objectMapper.readValue(
                                task.getPayload() != null && !task.getPayload().isEmpty() ? task.getPayload() : "{}",
                                new TypeReference<Map<String, Object>>() {}
                        );

                        String result = executor.execute(payload);

                        String cancelKey = "dtq:cancel:" + taskId;
                        if (Boolean.TRUE.equals(redisTemplate.hasKey(cancelKey))) {
                            task.setStatus(TaskStatus.CANCELLED);
                            task.setCompletedAt(LocalDateTime.now());
                            taskRepository.save(task);
                            redisTemplate.delete(cancelKey);
                            log.info("Task {} cancelled during execution", taskId);
                            continue;
                        }

                        task.setStatus(TaskStatus.SUCCESS);
                        task.setResult(result);
                        task.setCompletedAt(LocalDateTime.now());
                        taskRepository.save(task);
                        log.info("Task {} completed successfully", taskId);
                        
                    } catch (Exception e) {
                        handleTaskFailure(taskId, e);
                    } finally {
                        org.slf4j.MDC.remove("taskId");
                        org.slf4j.MDC.remove("workerId");
                        redisTemplate.delete(lockKey);
                        workerService.updateWorkerStatus(workerId, WorkerStatus.IDLE, null);
                    }

                } catch (Exception e) {
                    log.error("Worker {} encountered unexpected error processing task {}", workerId, taskId, e);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            } catch (Exception e) {
                log.error("Worker {} encountered unexpected error", workerId, e);
                try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        log.info("Worker {} stopped", workerId);
    }

    private void handleTaskFailure(UUID taskId, Exception e) {
        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) return;
        
        Task task = taskOpt.get();
        task.setRetryCount(task.getRetryCount() + 1);
        
        if (task.getRetryCount() <= task.getMaxRetries()) {
            task.setStatus(TaskStatus.RETRYING);
            long delay = RetryDelayCalculator.calculateDelay(task.getRetryCount(), baseRetryDelay, maxRetryDelay);
            log.info("Task {} failed. Retrying in {} ms (attempt {}/{})", taskId, delay, task.getRetryCount(), task.getMaxRetries());
            taskRepository.save(task);
            
            // simple delay implementation for now
            new Thread(() -> {
                try {
                    Thread.sleep(delay);
                    task.setStatus(TaskStatus.QUEUED);
                    taskRepository.save(task);
                    queueService.enqueue(taskId, task.getPriority());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        } else {
            task.setStatus(TaskStatus.FAILED);
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            if (errorMsg.length() > 2000) errorMsg = errorMsg.substring(0, 2000);
            task.setError(errorMsg);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
            deadLetterQueueService.moveToDlq(taskId);
            log.error("Task {} failed permanently", taskId, e);
        }
    }

    public void stop() {
        this.running = false;
    }
}
