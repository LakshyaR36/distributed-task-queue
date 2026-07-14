package com.taskqueue.entity;

public enum TaskStatus {
    PENDING,
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    RETRYING,
    CANCELLED
}
