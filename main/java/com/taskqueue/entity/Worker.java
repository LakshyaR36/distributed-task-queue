package com.taskqueue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "workers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Worker {

    @Id
    @Column(length = 100)
    private String workerId;

    @Column(nullable = false)
    private String hostname;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private WorkerStatus status = WorkerStatus.IDLE;

    @Column(nullable = false)
    private LocalDateTime lastHeartbeat;

    private UUID currentTask;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.lastHeartbeat = now;
    }
}
