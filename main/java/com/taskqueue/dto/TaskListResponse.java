package com.taskqueue.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskListResponse {

    private List<TaskResponse> tasks;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private int pageSize;
}
