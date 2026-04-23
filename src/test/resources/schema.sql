CREATE TABLE IF NOT EXISTS notification_inbox (
    event_id VARCHAR(100) NOT NULL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    payload JSON,
    retry_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME
);

CREATE INDEX idx_status_created_at ON notification_inbox (status, created_at);
CREATE INDEX idx_status_retry_count ON notification_inbox (status, retry_count);