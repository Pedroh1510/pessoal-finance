CREATE TABLE upload_jobs (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    type              VARCHAR(30)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    user_id           VARCHAR(255) NOT NULL,
    file_path         VARCHAR(500),
    original_filename VARCHAR(255),
    result_json       JSONB,
    error_message     TEXT,
    attempt_count     SMALLINT     NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_upload_jobs_status  ON upload_jobs(status);
CREATE INDEX idx_upload_jobs_user_id ON upload_jobs(user_id);
