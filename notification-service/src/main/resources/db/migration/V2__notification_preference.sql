-- Each driver's chosen notification channel. A driver without a row gets the configured default
-- (NOTIFICATION_CHANNEL, PUSH unless set). NotificationChannelFactory picks the channel from it.
CREATE TABLE notification_preference (
    driver_id VARCHAR(64) PRIMARY KEY,
    -- EMAIL, SMS, PUSH
    channel   VARCHAR(20) NOT NULL,
    -- Optimistic locking: two choices sent at once can't silently overwrite each other.
    version   BIGINT      NOT NULL
);
