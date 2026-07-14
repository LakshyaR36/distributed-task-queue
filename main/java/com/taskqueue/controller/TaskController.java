package com.taskqueue.controller;

import com.taskqueue.dto.TaskListResponse;
import com.taskqueue.dto.TaskRequest;
import com.taskqueue.dto.TaskResponse;
import com.taskqueue.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for task management operations.
 * Provides endpoints for task submission, retrieval, listing, cancellation, and retry.
 */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * Submits a new task for processing.
     *
     * @param request the validated task submission request
     * @return 201 Created with the task response
     */
    @PostMapping
    public ResponseEntity<TaskResponse> submitTask(@Valid @RequestBody TaskRequest request) {
        log.info("POST /api/tasks — submitting task: name={}, type={}", request.getTaskName(), request.getTaskType());
        TaskResponse response = taskService.submitTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a task by its ID.
     *
     * @param id the task UUID
     * @return 200 OK with the task response
     */
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable UUID id) {
        log.debug("GET /api/tasks/{}", id);
        TaskResponse response = taskService.getTask(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Lists tasks with pagination and optional filters.
     *
     * @param page     page index (default 0)
     * @param size     page size (default 20)
     * @param status   optional status filter
     * @param type     optional type filter
     * @param priority optional priority filter
     * @return 200 OK with the paginated task list
     */
    @GetMapping
    public ResponseEntity<TaskListResponse> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String priority) {
        log.debug("GET /api/tasks — page={}, size={}, status={}, type={}, priority={}", page, size, status, type, priority);
        TaskListResponse response = taskService.listTasks(page, size, status, type, priority);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancels a task by its ID.
     * For running tasks, a cancel flag is set in Redis for the worker to detect.
     *
     * @param id the task UUID
     * @return 200 OK with the updated task response
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<TaskResponse> cancelTask(@PathVariable UUID id) {
        log.info("DELETE /api/tasks/{}", id);
        TaskResponse response = taskService.cancelTask(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Retries a failed or cancelled task.
     *
     * @param id the task UUID
     * @return 200 OK with the updated task response
     */
    @PatchMapping("/{id}/retry")
    public ResponseEntity<TaskResponse> retryTask(@PathVariable UUID id) {
        log.info("PATCH /api/tasks/{}/retry", id);
        TaskResponse response = taskService.retryTask(id);
        return ResponseEntity.ok(response);
    }

    // --- Dead Letter Queue Endpoints ---

    @GetMapping("/dlq")
    public ResponseEntity<java.util.List<UUID>> listDlq() {
        return ResponseEntity.ok(taskService.listDlq());
    }

    @DeleteMapping("/dlq/{id}")
    public ResponseEntity<Void> removeFromDlq(@PathVariable UUID id) {
        taskService.removeFromDlq(id);
        return ResponseEntity.ok().build();
    }
}
