CREATE TABLE customer_record (
    customer_id VARCHAR(255) PRIMARY KEY,
    phone       VARCHAR(20)  NOT NULL,
    full_name   VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_customer_phone ON customer_record (phone);

CREATE TABLE onboarding_session (
    id                VARCHAR(36)  PRIMARY KEY,
    workflow_id       VARCHAR(255) NOT NULL,
    vendor_id         VARCHAR(255) NOT NULL,
    phone             VARCHAR(20),
    last_known_status VARCHAR(50),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_onboarding_session_workflow_id ON onboarding_session (workflow_id);
CREATE INDEX idx_session_phone_created ON onboarding_session (phone, created_at);

CREATE TABLE audit_log_entry (
    id         VARCHAR(255) PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    event      VARCHAR(255) NOT NULL,
    detail     JSONB,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_session_id ON audit_log_entry (session_id);