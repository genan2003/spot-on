-- Each driver's chosen payment method and simulated wallet. Created the first time a driver is seen
-- (no driver accounts exist anywhere else). The Factory picks the processor from preferred_method.
CREATE TABLE payment_account (
    driver_id        VARCHAR(64)    PRIMARY KEY,
    -- CARD, WALLET
    preferred_method VARCHAR(20)    NOT NULL,
    wallet_balance   NUMERIC(10, 2) NOT NULL CHECK (wallet_balance >= 0),
    -- Optimistic locking for choose/top-up; paying from the wallet locks the row (FOR UPDATE) instead.
    version          BIGINT         NOT NULL
);
