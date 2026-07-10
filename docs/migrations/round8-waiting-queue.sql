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
