package com.taskqueue.repository;

import com.taskqueue.entity.Task;
import com.taskqueue.entity.TaskPriority;
import com.taskqueue.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByTaskType(String taskType);

    List<Task> findByPriority(TaskPriority priority);

    Page<Task> findByStatusIn(List<TaskStatus> statuses, Pageable pageable);

    List<Task> findByStatusAndScheduledTimeLessThanEqual(TaskStatus status, LocalDateTime time);

    Optional<Task> findByPayloadHashAndStatusIn(String payloadHash, List<TaskStatus> statuses);

    List<Task> findByInDlqTrue();

    long countByStatus(TaskStatus status);

    long countByPriority(TaskPriority priority);

    @Query("SELECT t.status, COUNT(t) FROM Task t GROUP BY t.status")
    List<Object[]> countByStatusGroup();

    @Query("SELECT t.taskType, COUNT(t) FROM Task t GROUP BY t.taskType")
    List<Object[]> countByTypeGroup();

    // findAll(Pageable pageable) is inherited from JpaRepository
}
