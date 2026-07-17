-- Round 7 Kafka outbox hardening migration for MySQL 8.0.
-- Apply before deploying a relay that claims rows by their stored publication route.

SET @current_schema = DATABASE();

SET @topic_name_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @current_schema
      AND table_name = 'outbox_events'
      AND column_name = 'topic_name'
);
SET @add_topic_name_sql = IF(
    @topic_name_exists = 0,
    'ALTER TABLE `outbox_events` ADD COLUMN `topic_name` VARCHAR(255) NULL AFTER `aggregate_id`',
    'SELECT 1'
);
PREPARE add_topic_name_statement FROM @add_topic_name_sql;
EXECUTE add_topic_name_statement;
DEALLOCATE PREPARE add_topic_name_statement;

SET @partition_key_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @current_schema
      AND table_name = 'outbox_events'
      AND column_name = 'partition_key'
);
SET @add_partition_key_sql = IF(
    @partition_key_exists = 0,
    'ALTER TABLE `outbox_events` ADD COLUMN `partition_key` VARCHAR(255) NULL AFTER `topic_name`',
    'SELECT 1'
);
PREPARE add_partition_key_statement FROM @add_partition_key_sql;
EXECUTE add_partition_key_statement;
DEALLOCATE PREPARE add_partition_key_statement;

SET @claim_id_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @current_schema
      AND table_name = 'outbox_events'
      AND column_name = 'claim_id'
);
SET @add_claim_id_sql = IF(
    @claim_id_exists = 0,
    'ALTER TABLE `outbox_events` ADD COLUMN `claim_id` BINARY(16) NULL AFTER `published_at`',
    'SELECT 1'
);
PREPARE add_claim_id_statement FROM @add_claim_id_sql;
EXECUTE add_claim_id_statement;
DEALLOCATE PREPARE add_claim_id_statement;

SET @claimed_at_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @current_schema
      AND table_name = 'outbox_events'
      AND column_name = 'claimed_at'
);
SET @add_claimed_at_sql = IF(
    @claimed_at_exists = 0,
    'ALTER TABLE `outbox_events` ADD COLUMN `claimed_at` DATETIME(6) NULL AFTER `claim_id`',
    'SELECT 1'
);
PREPARE add_claimed_at_statement FROM @add_claimed_at_sql;
EXECUTE add_claimed_at_statement;
DEALLOCATE PREPARE add_claimed_at_statement;

-- Backfill only business events that have an external Kafka contract.
-- PAYMENT_APPROVED, PAYMENT_FAILED, and PAYMENT_STATUS_SYNC_REQUESTED stay route-less internal rows.
UPDATE `outbox_events`
SET `topic_name` = NULL,
    `partition_key` = NULL
WHERE `event_type` IN (
    'PAYMENT_APPROVED',
    'PAYMENT_FAILED',
    'PAYMENT_STATUS_SYNC_REQUESTED'
);

UPDATE `outbox_events`
SET `topic_name` = CASE
        WHEN `event_type` IN ('LIKE_COUNT_CHANGED_V1', 'PRODUCT_VIEWED_V1') THEN 'catalog-events'
        WHEN `event_type` IN ('ORDER_CREATED_V1', 'ORDER_PAID_V1', 'ORDER_FAILED_V1') THEN 'order-events'
        WHEN `event_type` = 'COUPON_ISSUE_REQUESTED_V1' THEN 'coupon-issue-requests'
        ELSE `topic_name`
    END,
    `partition_key` = CASE
        WHEN `event_type` = 'COUPON_ISSUE_REQUESTED_V1'
            THEN JSON_UNQUOTE(JSON_EXTRACT(`payload`, '$.couponTemplateId'))
        WHEN `event_type` IN (
            'LIKE_COUNT_CHANGED_V1',
            'PRODUCT_VIEWED_V1',
            'ORDER_CREATED_V1',
            'ORDER_PAID_V1',
            'ORDER_FAILED_V1'
        ) THEN CAST(`aggregate_id` AS CHAR)
        ELSE `partition_key`
    END
WHERE `event_type` IN (
    'LIKE_COUNT_CHANGED_V1',
    'PRODUCT_VIEWED_V1',
    'ORDER_CREATED_V1',
    'ORDER_PAID_V1',
    'ORDER_FAILED_V1',
    'COUPON_ISSUE_REQUESTED_V1'
);

SET @route_pair_check_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = @current_schema
      AND table_name = 'outbox_events'
      AND constraint_name = 'chk_outbox_events_publication_route_pair'
      AND constraint_type = 'CHECK'
);
SET @add_route_pair_check_sql = IF(
    @route_pair_check_exists = 0,
    'ALTER TABLE `outbox_events` ADD CONSTRAINT `chk_outbox_events_publication_route_pair` CHECK ((`topic_name` IS NULL AND `partition_key` IS NULL) OR (`topic_name` IS NOT NULL AND `partition_key` IS NOT NULL))',
    'SELECT 1'
);
PREPARE add_route_pair_check_statement FROM @add_route_pair_check_sql;
EXECUTE add_route_pair_check_statement;
DEALLOCATE PREPARE add_route_pair_check_statement;

SET @retry_claim_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = @current_schema
      AND table_name = 'outbox_events'
      AND index_name = 'idx_outbox_events_status_next_retry_event_created'
);
SET @add_retry_claim_index_sql = IF(
    @retry_claim_index_exists = 0,
    'CREATE INDEX `idx_outbox_events_status_next_retry_event_created` ON `outbox_events` (`event_status`, `next_retry_at`, `event_created_at`, `id`)',
    'SELECT 1'
);
PREPARE add_retry_claim_index_statement FROM @add_retry_claim_index_sql;
EXECUTE add_retry_claim_index_statement;
DEALLOCATE PREPARE add_retry_claim_index_statement;

SET @stale_claim_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = @current_schema
      AND table_name = 'outbox_events'
      AND index_name = 'idx_outbox_events_status_claimed_event_created'
);
SET @add_stale_claim_index_sql = IF(
    @stale_claim_index_exists = 0,
    'CREATE INDEX `idx_outbox_events_status_claimed_event_created` ON `outbox_events` (`event_status`, `claimed_at`, `event_created_at`, `id`)',
    'SELECT 1'
);
PREPARE add_stale_claim_index_statement FROM @add_stale_claim_index_sql;
EXECUTE add_stale_claim_index_statement;
DEALLOCATE PREPARE add_stale_claim_index_statement;

-- This must return no rows before deploying the route-driven relay.
SELECT `event_id`, `event_type`, `topic_name`, `partition_key`
FROM `outbox_events`
WHERE `event_type` IN (
    'LIKE_COUNT_CHANGED_V1',
    'PRODUCT_VIEWED_V1',
    'ORDER_CREATED_V1',
    'ORDER_PAID_V1',
    'ORDER_FAILED_V1',
    'COUPON_ISSUE_REQUESTED_V1'
)
  AND (`topic_name` IS NULL OR `partition_key` IS NULL);
