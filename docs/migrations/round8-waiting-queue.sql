-- Round 8 schema migration for MySQL 8.0.
-- Apply during a maintenance window before deploying the Round 8 commerce-api.

SET @current_schema = DATABASE();

-- Product sale type: preserve all existing products as NORMAL, then enforce the application contract.
SET @sale_type_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @current_schema
      AND table_name = 'products'
      AND column_name = 'sale_type'
);
SET @add_sale_type_sql = IF(
    @sale_type_exists = 0,
    'ALTER TABLE `products` ADD COLUMN `sale_type` VARCHAR(32) NULL DEFAULT ''NORMAL'' AFTER `price`',
    'SELECT 1'
);
PREPARE add_sale_type_statement FROM @add_sale_type_sql;
EXECUTE add_sale_type_statement;
DEALLOCATE PREPARE add_sale_type_statement;

UPDATE `products`
SET `sale_type` = 'NORMAL'
WHERE `sale_type` IS NULL;

ALTER TABLE `products`
    MODIFY COLUMN `sale_type` VARCHAR(32) NOT NULL DEFAULT 'NORMAL';

-- Replace the legacy globally unique idempotency key with a user-scoped unique key.
SET @legacy_idempotency_index = (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = @current_schema
      AND table_name = 'orders'
      AND non_unique = 0
      AND index_name <> 'PRIMARY'
    GROUP BY index_name
    HAVING COUNT(*) = 1
       AND MAX(column_name = 'idempotency_key') = 1
    LIMIT 1
);
SET @drop_legacy_idempotency_sql = IF(
    @legacy_idempotency_index IS NULL,
    'SELECT 1',
    CONCAT(
        'ALTER TABLE `orders` DROP INDEX `',
        REPLACE(@legacy_idempotency_index, '`', '``'),
        '`'
    )
);
PREPARE drop_legacy_idempotency_statement FROM @drop_legacy_idempotency_sql;
EXECUTE drop_legacy_idempotency_statement;
DEALLOCATE PREPARE drop_legacy_idempotency_statement;

SET @user_scoped_idempotency_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = @current_schema
          AND table_name = 'orders'
          AND non_unique = 0
          AND index_name <> 'PRIMARY'
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'ordered_user_id,idempotency_key'
    ) AS matching_indexes
);
SET @add_user_scoped_idempotency_sql = IF(
    @user_scoped_idempotency_exists = 0,
    'ALTER TABLE `orders` ADD CONSTRAINT `uk_orders_ordered_user_id_idempotency_key` UNIQUE (`ordered_user_id`, `idempotency_key`)',
    'SELECT 1'
);
PREPARE add_user_scoped_idempotency_statement FROM @add_user_scoped_idempotency_sql;
EXECUTE add_user_scoped_idempotency_statement;
DEALLOCATE PREPARE add_user_scoped_idempotency_statement;
