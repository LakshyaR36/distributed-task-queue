package com.taskqueue.repository;

import com.taskqueue.entity.Worker;
import com.taskqueue.entity.WorkerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, String> {

    List<Worker> findByStatus(WorkerStatus status);

    List<Worker> findByLastHeartbeatBefore(LocalDateTime threshold);

    long countByStatus(WorkerStatus status);
}
