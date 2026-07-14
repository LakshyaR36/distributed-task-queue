package com.taskqueue.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskqueue.dto.TaskListResponse;
import com.taskqueue.dto.TaskResponse;
import com.taskqueue.dto.TaskRequest;
import com.taskqueue.entity.Task;
import com.taskqueue.entity.TaskPriority;
import com.taskqueue.entity.TaskStatus;
import com.taskqueue.entity.TaskType;
import com.taskqueue.exception.DuplicateTaskException;
import com.taskqueue.exception.InvalidTaskStateException;
import com.taskqueue.exception.TaskNotFoundException;
import com.taskqueue.metrics.TaskMetrics;
import com.taskqueue.queue.DeadLetterQueueService;
import com.taskqueue.queue.RedisQueueService;
import com.taskqueue.repository.TaskRepository;
import com.taskqueue.util.PayloadHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core service for task lifecycle management.
 * Handles submission, retrieval, cancellation, and retry of tasks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private static final String CANCEL_KEY_PREFIX = "dtq:cancel:";
    private static final Duration CANCEL_FLAG_TTL = Duration.ofSeconds(60);

    private final TaskRepository taskRepository;
    private final RedisQueueService redisQueueService;
    private final DeadLetterQueueService dlqService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final TaskMetrics taskMetrics;

    /**
     * Submits a new task for processing.
     *
     * @param request the task submission request
     * @return the created task response
     * @throws IllegalArgumentException if the task type is invalid
     * @throws DuplicateTaskException   if a duplicate task already exists in an active state
     */
    @Transactional
    public TaskResponse submitTask(TaskRequest request) {
        log.info("Submitting task: name={}, type={}", request.getTaskName(), request.getTaskType());

        // Validate task type against the TaskType enum
        validateTaskType(request.getTaskType());

        // Serialize payload to JSON
        String payloadJson = serializePayload(request.getPayload());

        // Generate deduplication hash
        String payloadHash = PayloadHasher.hash(request.getTaskType(), request.getPayload());

        // Check for duplicates in active states
        checkForDuplicates(payloadHash);

        // Parse priority with NORMAL as default
        TaskPriority priority = parsePriority(request.getPriority());

        // Build the task entity
        LocalDateTime now = LocalDateTime.now();
        Task task = Task.builder()
                .id(UUID.randomUUID())
                .taskName(request.getTaskName())
                .taskType(request.getTaskType())
                .payload(payloadJson)
                .priority(priority)
                .status(TaskStatus.PENDING)
                .retryCount(0)
                .maxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : 3)
                .scheduledTime(request.getScheduledTime())
                .createdAt(now)
                .updatedAt(now)
                .payloadHash(payloadHash)
                .inDlq(false)
                .build();

        task = taskRepository.save(task);
        log.debug("Task saved to database: id={}", task.getId());

        // Enqueue immediately if no future schedule
        if (request.getScheduledTime() == null || !request.getScheduledTime().isAfter(now)) {
            redisQueueService.enqueue(task.getId(), priority);
            task.setStatus(TaskStatus.QUEUED);
            task.setUpdatedAt(LocalDateTime.now());
            task = taskRepository.save(task);
            log.info("Task enqueued immediately: id={}, priority={}", task.getId(), priority);
        } else {
            log.info("Task scheduled for future execution: id={}, scheduledTime={}",
                    task.getId(), request.getScheduledTime());
        }

        taskMetrics.incrementSubmitted();
        return mapToResponse(task);
    }

    /**
     * Retrieves a task by its ID.
     *
     * @param id the task UUID
     * @return the task response
     * @throws TaskNotFoundException if the task does not exist
     */
    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID id) {
        log.debug("Fetching task: id={}", id);
        Task task = findTaskOrThrow(id);
        return mapToResponse(task);
    }

    /**
     * Lists tasks with pagination and optional filtering.
     *
     * @param page     zero-based page index
     * @param size     page size
     * @param status   optional status filter
     * @param type     optional type filter
     * @param priority optional priority filter
     * @return paginated task list response
     */
    @Transactional(readOnly = true)
    public TaskListResponse listTasks(int page, int size, String status, String type, String priority) {
        log.debug("Listing tasks: page={}, size={}, status={}, type={}, priority={}", page, size, status, type, priority);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Task> taskPage = taskRepository.findAll(pageable);

        // Apply in-memory filters if provided
        List<TaskResponse> tasks = taskPage.getContent().stream()
                .filter(task -> status == null || status.isBlank() || task.getStatus().name().equalsIgnoreCase(status))
                .filter(task -> type == null || type.isBlank() || task.getTaskType().equalsIgnoreCase(type))
                .filter(task -> priority == null || priority.isBlank() || task.getPriority().name().equalsIgnoreCase(priority))
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return TaskListResponse.builder()
                .tasks(tasks)
                .currentPage(taskPage.getNumber())
                .totalPages(taskPage.getTotalPages())
                .totalElements(taskPage.getTotalElements())
                .pageSize(taskPage.getSize())
                .build();
    }

    /**
     * Cancels a task. For RUNNING tasks, sets a cancel flag in Redis for the worker to pick up.
     *
     * @param id the task UUID
     * @return the updated task response
     * @throws TaskNotFoundException     if the task does not exist
     * @throws InvalidTaskStateException if the task is in a terminal state
     */
    @Transactional
    public TaskResponse cancelTask(UUID id) {
        log.info("Cancelling task: id={}", id);
        Task task = findTaskOrThrow(id);

        // Cannot cancel tasks already in terminal states
        if (task.getStatus() == TaskStatus.SUCCESS
                || task.getStatus() == TaskStatus.FAILED
                || task.getStatus() == TaskStatus.CANCELLED) {
            throw new InvalidTaskStateException(
                    "Cannot cancel task in state: " + task.getStatus());
        }

        if (task.getStatus() == TaskStatus.PENDING || task.getStatus() == TaskStatus.QUEUED) {
            // Remove from Redis queue and mark as cancelled
            redisQueueService.removeFromQueue(task.getId());
            task.setStatus(TaskStatus.CANCELLED);
            log.info("Task removed from queue and cancelled: id={}", id);
        } else if (task.getStatus() == TaskStatus.RUNNING) {
            // Set cancel flag in Redis for the worker to detect
            String cancelKey = CANCEL_KEY_PREFIX + task.getId().toString();
            stringRedisTemplate.opsForValue().set(cancelKey, "true", CANCEL_FLAG_TTL);
            task.setStatus(TaskStatus.CANCELLED);
            log.info("Cancel flag set in Redis for running task: id={}", id);
        }

        task.setUpdatedAt(LocalDateTime.now());
        task = taskRepository.save(task);
        return mapToResponse(task);
    }

    /**
     * Retries a failed or cancelled task by resetting its state and re-enqueuing.
     *
     * @param id the task UUID
     * @return the updated task response
     * @throws TaskNotFoundException     if the task does not exist
     * @throws InvalidTaskStateException if the task is not in FAILED or CANCELLED state
     */
    @Transactional
    public TaskResponse retryTask(UUID id) {
        log.info("Retrying task: id={}", id);
        Task task = findTaskOrThrow(id);

        if (task.getStatus() != TaskStatus.FAILED && task.getStatus() != TaskStatus.CANCELLED) {
            throw new InvalidTaskStateException(
                    "Can only retry tasks in FAILED or CANCELLED state. Current state: " + task.getStatus());
        }

        // Reset task state for retry
        task.setRetryCount(0);
        task.setStatus(TaskStatus.PENDING);
        task.setError(null);
        task.setResult(null);
        task.setStartedAt(null);
        task.setCompletedAt(null);
        task.setInDlq(false);
        task.setUpdatedAt(LocalDateTime.now());
        task = taskRepository.save(task);

        // Enqueue and update status to QUEUED
        redisQueueService.enqueue(task.getId(), task.getPriority());
        task.setStatus(TaskStatus.QUEUED);
        task.setUpdatedAt(LocalDateTime.now());
        task = taskRepository.save(task);

        log.info("Task re-enqueued for retry: id={}, priority={}", id, task.getPriority());
        return mapToResponse(task);
    }

    public List<UUID> listDlq() {
        return dlqService.listDlq();
    }

    public void removeFromDlq(UUID id) {
        dlqService.removeFromDlq(id);
    }

    /**
     * Maps a Task entity to a TaskResponse DTO.
     */
    private TaskResponse mapToResponse(Task task) {
        Long durationMs = null;
        if (task.getStartedAt() != null && task.getCompletedAt() != null) {
            durationMs = Duration.between(task.getStartedAt(), task.getCompletedAt()).toMillis();
        } else if (task.getStartedAt() != null && task.getStatus() == TaskStatus.RUNNING) {
            durationMs = Duration.between(task.getStartedAt(), LocalDateTime.now()).toMillis();
        }

        boolean retryable = (task.getStatus() == TaskStatus.FAILED || task.getStatus() == TaskStatus.CANCELLED);

        return TaskResponse.builder()
                .id(task.getId())
                .taskName(task.getTaskName())
                .taskType(task.getTaskType())
                .payload(task.getPayload())
                .priority(task.getPriority() != null ? task.getPriority().name() : null)
                .status(task.getStatus() != null ? task.getStatus().name() : null)
                .retryCount(task.getRetryCount())
                .maxRetries(task.getMaxRetries())
                .scheduledTime(task.getScheduledTime())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .workerId(task.getWorkerId())
                .result(task.getResult())
                .error(task.getError())
                .inDlq(task.isInDlq())
                .durationMs(durationMs)
                .retryable(retryable)
                .build();
    }

    private Task findTaskOrThrow(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + id));
    }

    private void validateTaskType(String taskType) {
        try {
            TaskType.valueOf(taskType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid task type: " + taskType
                    + ". Valid types: " + java.util.Arrays.toString(TaskType.values()));
        }
    }

    private String serializePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payload", e);
            throw new IllegalArgumentException("Invalid payload: unable to serialize to JSON", e);
        }
    }

    private void checkForDuplicates(String payloadHash) {
        List<TaskStatus> activeStatuses = List.of(TaskStatus.PENDING, TaskStatus.QUEUED, TaskStatus.RUNNING);
        taskRepository.findByPayloadHashAndStatusIn(payloadHash, activeStatuses)
                .ifPresent(existing -> {
                    throw new DuplicateTaskException(
                            "Duplicate task detected. Existing task ID: " + existing.getId());
                });
    }

    private TaskPriority parsePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return TaskPriority.NORMAL;
        }
        try {
            return TaskPriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid priority '{}', defaulting to NORMAL", priority);
            return TaskPriority.NORMAL;
        }
    }
}
