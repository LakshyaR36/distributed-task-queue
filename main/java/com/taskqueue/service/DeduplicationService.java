package com.taskqueue.service;

import com.taskqueue.entity.TaskStatus;
import com.taskqueue.repository.TaskRepository;
import com.taskqueue.util.PayloadHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Service for task deduplication based on payload hashing.
 * Prevents submission of duplicate tasks that are still being processed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private final TaskRepository taskRepository;

    /**
     * Checks whether a task with the given payload hash already exists
     * in an active state (PENDING, QUEUED, or RUNNING).
     *
     * @param payloadHash the SHA-256 hash of the task type and payload
     * @return true if a duplicate exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isDuplicate(String payloadHash) {
        List<TaskStatus> activeStatuses = List.of(
                TaskStatus.PENDING,
                TaskStatus.QUEUED,
                TaskStatus.RUNNING
        );

        boolean duplicate = taskRepository
                .findByPayloadHashAndStatusIn(payloadHash, activeStatuses)
                .isPresent();

        if (duplicate) {
            log.debug("Duplicate detected for payloadHash={}", payloadHash);
        }

        return duplicate;
    }

    /**
     * Generates a deterministic hash for the given task type and payload.
     * Delegates to {@link PayloadHasher#hash(String, Map)}.
     *
     * @param taskType the task type string
     * @param payload  the task payload map
     * @return the computed SHA-256 hash
     */
    public String generateHash(String taskType, Map<String, Object> payload) {
        return PayloadHasher.hash(taskType, payload);
    }
}
