package com.taskqueue.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskResponse {

    private UUID id;
    private String taskName;
    private String taskType;
    private String payload;
    private String priority;
    private String status;
    private int retryCount;
    private int maxRetries;
    private LocalDateTime scheduledTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String workerId;
    private String result;
    private String error;
    private String payloadHash;
    private boolean inDlq;

    // Computed fields
    private Long duration;
    private boolean retryable;
}
