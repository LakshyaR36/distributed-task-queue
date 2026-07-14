package com.taskqueue.entity;

public enum TaskPriority {
    LOW(0),
    NORMAL(1),
    HIGH(2),
    CRITICAL(3);

    private final int order;

    TaskPriority(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }
}
