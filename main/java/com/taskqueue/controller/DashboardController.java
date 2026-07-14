package com.taskqueue.controller;

import com.taskqueue.dto.DashboardStats;
import com.taskqueue.dto.TaskResponse;
import com.taskqueue.dto.WorkerInfo;
import com.taskqueue.entity.Task;
import com.taskqueue.entity.TaskStatus;
import com.taskqueue.entity.WorkerStatus;
import com.taskqueue.queue.RedisQueueService;
import com.taskqueue.repository.TaskRepository;
import com.taskqueue.repository.WorkerRepository;
import com.taskqueue.service.TaskService;
import com.taskqueue.service.WorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for the dashboard UI and its backing stats API.
 * Serves a Thymeleaf template and exposes a JSON endpoint for real-time stats.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final TaskService taskService;
    private final WorkerService workerService;
    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;
    private final RedisQueueService redisQueueService;

    /**
     * Serves the Thymeleaf dashboard page.
     *
     * @return the template name "dashboard"
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    /**
     * Returns real-time dashboard statistics as JSON.
     * Aggregates task counts, queue sizes, worker status, and recent tasks.
     *
     * @return DashboardStats with all metrics
     */
    @GetMapping("/api/dashboard/stats")
    @ResponseBody
    public DashboardStats getStats() {
        log.debug("GET /api/dashboard/stats");

        // Total task count
        long totalTasks = taskRepository.count();

        // Counts by status
        long pendingCount = taskRepository.countByStatus(TaskStatus.PENDING);
        long queuedCount = taskRepository.countByStatus(TaskStatus.QUEUED);
        long runningCount = taskRepository.countByStatus(TaskStatus.RUNNING);
        long successCount = taskRepository.countByStatus(TaskStatus.SUCCESS);
        long failedCount = taskRepository.countByStatus(TaskStatus.FAILED);
        long cancelledCount = taskRepository.countByStatus(TaskStatus.CANCELLED);
        long retryingCount = taskRepository.countByStatus(TaskStatus.RETRYING);

        // Queue sizes from Redis
        Map<String, Long> queueSizes = redisQueueService.getQueueSizes();

        // Worker counts
        long totalWorkers = workerRepository.count();
        long idleWorkers = workerService.countByStatus(WorkerStatus.IDLE);
        long busyWorkers = workerService.countByStatus(WorkerStatus.BUSY);
        long offlineWorkers = workerService.countByStatus(WorkerStatus.OFFLINE);

        // Status distribution (from grouped query)
        List<Map<String, Object>> statusDistribution = taskRepository.countByStatusGroup();

        // Type distribution (from grouped query)
        List<Map<String, Object>> typeDistribution = taskRepository.countByTypeGroup();

        // Workers list
        List<WorkerInfo> workers = workerService.getAllWorkers();

        // Recent 10 tasks
        PageRequest recentPage = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<TaskResponse> recentTasks = taskRepository.findAll(recentPage)
                .getContent()
                .stream()
                .map(task -> taskService.getTask(task.getId()))
                .collect(Collectors.toList());

        return DashboardStats.builder()
                .totalTasks(totalTasks)
                .pendingCount(pendingCount)
                .queuedCount(queuedCount)
                .runningCount(runningCount)
                .successCount(successCount)
                .failedCount(failedCount)
                .cancelledCount(cancelledCount)
                .retryingCount(retryingCount)
                .queueSizes(queueSizes)
                .totalWorkers(totalWorkers)
                .idleWorkers(idleWorkers)
                .busyWorkers(busyWorkers)
                .offlineWorkers(offlineWorkers)
                .statusDistribution(statusDistribution)
                .typeDistribution(typeDistribution)
                .workers(workers)
                .recentTasks(recentTasks)
                .build();
    }
}
