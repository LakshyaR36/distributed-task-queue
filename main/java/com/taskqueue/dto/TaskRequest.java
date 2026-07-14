package com.taskqueue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskRequest {

    @NotBlank(message = "Task name is required")
    private String taskName;

    @NotBlank(message = "Task type is required")
    private String taskType;

    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    @Builder.Default
    private String priority = "NORMAL";

    @Builder.Default
    private Integer maxRetries = 3;

    private LocalDateTime scheduledTime;
}
