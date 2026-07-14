-- ============================================================
-- Distributed Task Queue - Database Schema
-- ============================================================

-- Tasks table
CREATE TABLE IF NOT EXISTS tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_name VARCHAR(255) NOT NULL,
    task_type VARCHAR(100) NOT NULL,
    payload JSONB DEFAULT '{}',
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    scheduled_time TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    worker_id VARCHAR(100),
    result TEXT,
    error TEXT,
    payload_hash VARCHAR(64),
    in_dlq BOOLEAN NOT NULL DEFAULT FALSE
);

-- Workers table
CREATE TABLE IF NOT EXISTS workers (
    worker_id VARCHAR(100) PRIMARY KEY,
    hostname VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    last_heartbeat TIMESTAMP NOT NULL DEFAULT NOW(),
    current_task UUID REFERENCES tasks(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Indexes
-- ============================================================

-- Task indexes
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_tasks_priority ON tasks(priority);
CREATE INDEX IF NOT EXISTS idx_tasks_scheduled_time ON tasks(scheduled_time);
CREATE INDEX IF NOT EXISTS idx_tasks_payload_hash ON tasks(payload_hash);
CREATE INDEX IF NOT EXISTS idx_tasks_in_dlq ON tasks(in_dlq);
CREATE INDEX IF NOT EXISTS idx_tasks_task_type ON tasks(task_type);
CREATE INDEX IF NOT EXISTS idx_tasks_created_at ON tasks(created_at);

-- Worker indexes
CREATE INDEX IF NOT EXISTS idx_workers_status ON workers(status);
CREATE INDEX IF NOT EXISTS idx_workers_last_heartbeat ON workers(last_heartbeat);
