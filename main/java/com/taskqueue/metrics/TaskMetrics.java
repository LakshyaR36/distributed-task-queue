package com.taskqueue.metrics;

import com.taskqueue.entity.TaskPriority;
import com.taskqueue.entity.TaskStatus;
import com.taskqueue.queue.RedisQueueService;
import com.taskqueue.repository.TaskRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskMetrics {
    private final MeterRegistry meterRegistry;
    private final RedisQueueService queueService;
    private final TaskRepository taskRepository;

    private Counter tasksSubmitted;
    private Counter tasksCompleted;
    private Counter tasksFailed;
    private Counter tasksRetried;
    private Timer executionTimer;

    @PostConstruct
    public void init() {
        tasksSubmitted = Counter.builder("dtq.tasks.submitted").register(meterRegistry);
        tasksCompleted = Counter.builder("dtq.tasks.completed").register(meterRegistry);
        tasksFailed = Counter.builder("dtq.tasks.failed").register(meterRegistry);
        tasksRetried = Counter.builder("dtq.tasks.retried").register(meterRegistry);
        executionTimer = Timer.builder("dtq.tasks.execution.time").register(meterRegistry);

        // Register queue size gauges
        for (TaskPriority priority : TaskPriority.values()) {
            meterRegistry.gauge("dtq.queue.size", 
                java.util.Collections.singletonList(io.micrometer.core.instrument.Tag.of("priority", priority.name())), 
                queueService, 
                qs -> qs.getQueueSize(priority));
        }

        // Register running task gauge
        meterRegistry.gauge("dtq.tasks.running", taskRepository, tr -> tr.countByStatus(TaskStatus.RUNNING));
    }

    public void incrementSubmitted() { tasksSubmitted.increment(); }
    public void incrementCompleted() { tasksCompleted.increment(); }
    public void incrementFailed() { tasksFailed.increment(); }
    public void incrementRetried() { tasksRetried.increment(); }
    public void recordExecutionTime(long millis) {
        executionTimer.record(java.time.Duration.ofMillis(millis));
    }
}
