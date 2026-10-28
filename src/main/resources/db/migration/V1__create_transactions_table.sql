-- Executed, rejected and failed transactions.
-- Money is NUMERIC(19,2) so that BigDecimal round-trips without rounding, and created_at is
-- TIMESTAMPTZ because the API exposes timestamps in UTC.
-- error_message is VARCHAR(500), not TEXT: provider rejection messages are short, and a bounded
-- column keeps an upper limit on what an upstream response can write here instead of allowing an
-- unbounded blob in a write-heavy table.
CREATE TABLE transactions
(
    id                      UUID           NOT NULL,
    account_id              VARCHAR(64)    NOT NULL,
    type                    VARCHAR(16)    NOT NULL,
    amount                  NUMERIC(19, 2) NOT NULL,
    currency                VARCHAR(3)     NOT NULL,
    description             VARCHAR(255),
    idempotency_key         VARCHAR(255),
    status                  VARCHAR(16)    NOT NULL,
    provider_transaction_id VARCHAR(255),
    balance_after           NUMERIC(19, 2),
    error_code              VARCHAR(64),
    error_message           VARCHAR(500),
    created_at              TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT uq_transactions_idempotency_key UNIQUE (idempotency_key)
);

-- GET /transactions orders by created_at DESC and can filter by account_id, so this composite
-- index serves the most selective filter together with the sort order. The single-column index
-- does the same for queries without filters.
-- There are no indexes on status or type: both are low cardinality columns (3 and 2 values), an
-- index scan would rarely beat a sequential scan, and every extra index is paid on each insert of
-- a write-heavy table.
CREATE INDEX idx_transactions_account_id_created_at ON transactions (account_id, created_at DESC);
CREATE INDEX idx_transactions_created_at ON transactions (created_at DESC);
