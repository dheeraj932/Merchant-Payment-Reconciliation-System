-- MPRS Database Schema - V1 Initial Migration

-- Users
CREATE TABLE users (
    id          BIGSERIAL       PRIMARY KEY,          
    username    VARCHAR(100)    NOT NULL UNIQUE,
    password    VARCHAR(255)    NOT NULL
    role        VARCHAR(50)     NOT NULL,       -- ADMIN, FINANCE_ANALYST, SYSTEM
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW()  
);

-- Transactions
CREATE TABLE transactions (
    id              BIGSERIAL       PRIMARY KEY,          
    transaction_id  VARCHAR(100)    NOT NULL UNIQUE,
    amount          NUMERIC(19, 4)  NOT NULL,
    status          VARCHAR(50)     NOT NULL,       -- AUTHORIZED, SETTLED, REFUNDED, CHARGEBACK
    settlement_date DATE            NOT NULL,
    merchant_id     VARCHAR(100)    NOT NULL,          
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Index for reconciliation (filter by settlement_date)
CREATE INDEX idx_transactions_settlement_date
    ON transactions (settlement_date);

CREATE INDEX idx_transactions_merchant_id
    ON transactions (merchant_id);

-- Payouts
CREATE TABLE payouts (
    id              BIGSERIAL       PRIMARY KEY,          
    transaction_id  VARCHAR(100)    NOT NULL UNIQUE,
    payout_amount   NUMERIC(19, 4)  NOT NULL,
    payout_date     VARCHAR(50)     NOT NULL,       
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW() 
)

-- Index for reconciliation (filter by payout_date)
CREATE INDEX idx_payouts_payout_date
    ON payouts (payout_date);

-- Reconciliation Jobs
CREATE TABLE reconciliation_jobs (
    id              BIGSERIAL       PRIMARY KEY,          
    job_id          VARCHAR(100)    NOT NULL UNIQUE,
    start_date      DATE            NOT NULL,
    end_date        DATE            NOT NULL,
    status          VARCHAR(50)     NOT NULL,        -- PENDING, RUNNING, COMPLETED, FAILED
    triggered_by    VARCHAR(100),          
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMP,          
    error_message   TEXT,

    CONSTRAINT uq_recon_window UNIQUE (start_date, end_date)
);

-- Reconciliation Results
CREATE TABLE reconciliation_results (
    id              BIGSERIAL       PRIMARY KEY,          
    job_id          VARCHAR(100)    NOT NULL,
    transaction_id  VARCHAR(100)    NOT NULL,
    merchant_id     VARCHAR(100)    NOT NULL,
    expected_amount NUMERIC(19, 4),
    paid_amount     NUMERIC(19, 4),          
    variance        NUMERIC(19, 4),
    exception_type  VARCHAR(50),          
    description     TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_recon_results_job
        FOREIGN KEY (job_id) REFERENCES reconciliation_jobs (job_id)
);

CREATE INDEX idx_recon_results_job_id
    ON reconciliation_results (job_id);

CREATE INDEX idx_recon_results_exception_type
    ON reconciliation_results (job_id, exception_type);

-- Seed Data 
-- BCrypt hash of "admin123"    -> for ADMIN user
-- BCrypt hash of "analyst123"  -> for FINANCE_ANALYST user
-- BCrypt hash of "system123"   -> for SYSTEM user
INSERT INTO users (username, password, role) VALUES
    ('admin',   '$2a$12$7Hz5dKaeCE5zSvFbMsTGreEbgpJxsJm1yCNpNBnunOGqeVMuiXT7i',    'ADMIN'),
    ('analyst', '$2a$12$Nz5F6VHoJSXbCT4o0GhAXOIkYexvXzUhCmxjxkV5kIBfAH.z9uI6K',    'FINANCE_ANALYST'),
    ('system',  '$2a$12$Xk4e1VqHm5pOjMnLxRzYt.Wqk09LJVBKS7TjPEq0KQ1YCaHvOjXSi',    'SYSTEM');