package com.taskqueue.controller;

import com.taskqueue.dto.WorkerInfo;
import com.taskqueue.service.WorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for worker management operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;

    /**
     * Lists all registered workers with their current status.
     *
     * @return 200 OK with the list of worker info DTOs
     */
    @GetMapping
    public ResponseEntity<List<WorkerInfo>> listWorkers() {
        log.debug("GET /api/workers");
        List<WorkerInfo> workers = workerService.getAllWorkers();
        return ResponseEntity.ok(workers);
    }
}
