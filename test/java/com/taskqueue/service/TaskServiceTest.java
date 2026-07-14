package com.taskqueue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskqueue.dto.TaskRequest;
import com.taskqueue.dto.TaskResponse;
import com.taskqueue.entity.Task;
import com.taskqueue.entity.TaskPriority;
import com.taskqueue.entity.TaskStatus;
import com.taskqueue.metrics.TaskMetrics;
import com.taskqueue.queue.DeadLetterQueueService;
import com.taskqueue.queue.RedisQueueService;
import com.taskqueue.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private RedisQueueService queueService;
    @Mock
    private DeadLetterQueueService dlqService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private TaskMetrics taskMetrics;

    @InjectMocks
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        // Assume basic mocked behavior for save
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> {
            Task t = (Task) i.getArguments()[0];
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });
    }

    @Test
    void testConcurrentTaskSubmission() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    TaskRequest request = new TaskRequest();
                    request.setTaskName("Load Test Task");
                    request.setTaskType("DUMMY_SLEEP");
                    request.setPriority("NORMAL");
                    
                    TaskResponse response = taskService.submitTask(request);
                    if (response != null && response.getId() != null) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        assertEquals(threadCount, successCount.get(), "All tasks should be submitted successfully under load.");
    }
}
