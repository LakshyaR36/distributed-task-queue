package com.taskqueue.executor;

import java.util.Map;

public interface TaskExecutor {
    String getType();
    String execute(Map<String, Object> payload) throws Exception;
}
