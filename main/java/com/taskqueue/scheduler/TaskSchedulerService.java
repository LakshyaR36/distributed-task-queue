package com.taskqueue.scheduler;

import com.taskqueue.entity.Task;
import com.taskqueue.entity.TaskStatus;
import com.taskqueue.queue.RedisQueueService;
import com.taskqueue.repository.TaskRepository;
import com.taskqueue.service.WorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class TaskSchedulerService {
    private final TaskRepository taskRepository;
    private final RedisQueueService queueService;
    private final WorkerService workerService;

    @Scheduled(fixedRate = 1000)
    public void scheduleReadyTasks() {
        List<Task> readyTasks = taskRepository.findByStatusAndScheduledTimeLessThanEqual(TaskStatus.PENDING, LocalDateTime.now());
        for (Task task : readyTasks) {
            queueService.enqueue(task.getId(), task.getPriority());
            task.setStatus(TaskStatus.QUEUED);
            taskRepository.save(task);
            log.info("Scheduled task {} enqueued", task.getId());
        }
    }

    @Scheduled(fixedRate = 10000)
    public void cleanupOfflineWorkers() {
        workerService.markOfflineWorkers();
    }
}
