package com.taskqueue.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerInfo {

    private String workerId;
    private String hostname;
    private String status;
    private LocalDateTime lastHeartbeat;
    private UUID currentTask;
}
