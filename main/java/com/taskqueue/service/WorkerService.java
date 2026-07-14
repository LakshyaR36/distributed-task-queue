package com.taskqueue.service;

import com.taskqueue.dto.WorkerInfo;
import com.taskqueue.entity.Worker;
import com.taskqueue.entity.WorkerStatus;
import com.taskqueue.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing worker registration, heartbeat, and lifecycle.
 */
@Slf4j
@Service
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final long offlineThresholdSeconds;

    public WorkerService(WorkerRepository workerRepository,
                         @Value("${app.worker.offline-threshold:30}") long offlineThresholdSeconds) {
        this.workerRepository = workerRepository;
        this.offlineThresholdSeconds = offlineThresholdSeconds;
    }

    /**
     * Registers a new worker or updates an existing one.
     *
     * @param workerId the unique worker identifier
     * @param hostname the worker's hostname
     * @return the registered or updated worker entity
     */
    @Transactional
    public Worker registerWorker(String workerId, String hostname) {
        log.info("Registering worker: id={}, hostname={}", workerId, hostname);

        Worker worker = workerRepository.findById(workerId)
                .map(existing -> {
                    existing.setHostname(hostname);
                    existing.setStatus(WorkerStatus.IDLE);
                    existing.setLastHeartbeat(LocalDateTime.now());
                    return existing;
                })
                .orElseGet(() -> Worker.builder()
                        .id(workerId)
                        .hostname(hostname)
                        .status(WorkerStatus.IDLE)
                        .lastHeartbeat(LocalDateTime.now())
                        .registeredAt(LocalDateTime.now())
                        .build());

        worker = workerRepository.save(worker);
        log.info("Worker registered successfully: id={}", workerId);
        return worker;
    }

    /**
     * Updates the heartbeat timestamp for a worker.
     *
     * @param workerId the worker identifier
     */
    @Transactional
    public void updateHeartbeat(String workerId) {
        workerRepository.findById(workerId).ifPresentOrElse(
                worker -> {
                    worker.setLastHeartbeat(LocalDateTime.now());
                    workerRepository.save(worker);
                    log.trace("Heartbeat updated for worker: id={}", workerId);
                },
                () -> log.warn("Heartbeat received for unknown worker: id={}", workerId)
        );
    }

    /**
     * Updates the status and current task of a worker.
     *
     * @param workerId    the worker identifier
     * @param status      the new worker status
     * @param currentTask the UUID of the task currently being processed, or null
     */
    @Transactional
    public void updateWorkerStatus(String workerId, WorkerStatus status, UUID currentTask) {
        workerRepository.findById(workerId).ifPresentOrElse(
                worker -> {
                    worker.setStatus(status);
                    worker.setCurrentTaskId(currentTask);
                    worker.setLastHeartbeat(LocalDateTime.now());
                    workerRepository.save(worker);
                    log.debug("Worker status updated: id={}, status={}, currentTask={}", workerId, status, currentTask);
                },
                () -> log.warn("Status update for unknown worker: id={}", workerId)
        );
    }

    /**
     * Returns all workers mapped to WorkerInfo DTOs.
     *
     * @return list of worker information
     */
    @Transactional(readOnly = true)
    public List<WorkerInfo> getAllWorkers() {
        return workerRepository.findAll().stream()
                .map(this::mapToWorkerInfo)
                .collect(Collectors.toList());
    }

    /**
     * Detects and marks workers whose heartbeat has expired as OFFLINE.
     * Clears their current task assignment.
     */
    @Transactional
    public void markOfflineWorkers() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(offlineThresholdSeconds);
        List<Worker> activeWorkers = workerRepository.findByStatus(WorkerStatus.IDLE);
        activeWorkers.addAll(workerRepository.findByStatus(WorkerStatus.BUSY));

        List<Worker> offlineWorkers = activeWorkers.stream()
                .filter(w -> w.getLastHeartbeat() != null && w.getLastHeartbeat().isBefore(threshold))
                .collect(Collectors.toList());

        for (Worker worker : offlineWorkers) {
            log.warn("Marking worker as OFFLINE due to expired heartbeat: id={}, lastHeartbeat={}",
                    worker.getId(), worker.getLastHeartbeat());
            worker.setStatus(WorkerStatus.OFFLINE);
            worker.setCurrentTaskId(null);
            workerRepository.save(worker);
        }

        if (!offlineWorkers.isEmpty()) {
            log.info("Marked {} worker(s) as OFFLINE", offlineWorkers.size());
        }
    }

    /**
     * Counts workers by status.
     *
     * @param status the worker status to count
     * @return the count
     */
    @Transactional(readOnly = true)
    public long countByStatus(WorkerStatus status) {
        return workerRepository.countByStatus(status);
    }

    private WorkerInfo mapToWorkerInfo(Worker worker) {
        return WorkerInfo.builder()
                .id(worker.getId())
                .hostname(worker.getHostname())
                .status(worker.getStatus() != null ? worker.getStatus().name() : null)
                .currentTaskId(worker.getCurrentTaskId())
                .lastHeartbeat(worker.getLastHeartbeat())
                .registeredAt(worker.getRegisteredAt())
                .build();
    }
}
