-- DSM 2.0 transactions table (simplified for the demo)
DROP TABLE IF EXISTS transactions;

CREATE TABLE transactions (
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY,
    transaction_id      VARCHAR(32)  NOT NULL UNIQUE,
    account_type        VARCHAR(16)  NOT NULL,   -- CHECKING / SAVINGS / CREDIT_CARD
    amount              DECIMAL(18,2) NOT NULL,
    merchant_name       VARCHAR(128) NOT NULL,
    enrichment_vendor   VARCHAR(16)  NOT NULL,   -- SPADE / MASTERCARD / CFGNATIVE
    enrichment_status   VARCHAR(16)  NOT NULL,   -- SUCCESS / FALLBACK / FAILED
    txn_date            DATE         NOT NULL
);
