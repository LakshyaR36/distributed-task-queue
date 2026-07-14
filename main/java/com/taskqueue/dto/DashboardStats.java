package com.taskqueue.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStats {

    private long totalTasks;
    private long pendingTasks;
    private long runningTasks;
    private long completedTasks;
    private long failedTasks;
    private long cancelledTasks;
    private long retryingTasks;
    private long dlqTasks;
    private Map<String, Long> queueSizes;
    private long activeWorkers;
    private long busyWorkers;
    private long offlineWorkers;
    private Map<String, Long> statusDistribution;
    private Map<String, Long> typeDistribution;
    private List<WorkerInfo> workers;
    private List<TaskResponse> recentTasks;
}
