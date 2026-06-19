#!/usr/bin/env bash
set -euo pipefail

DATA_DIR="/var/lib/mysql-files/dev-data"
DATABASE="${MYSQL_DATABASE:-loopers}"
REQUIRED_FILES=(
  "$DATA_DIR/brands.csv"
  "$DATA_DIR/products.csv"
  "$DATA_DIR/product_stocks.csv"
  "$DATA_DIR/product_stats.csv"
)

for file in "${REQUIRED_FILES[@]}"; do
  if [ ! -s "$file" ]; then
    echo "Skipping generated catalog LOAD DATA because $file is missing or empty."
    exit 0
  fi
done

echo "Loading generated catalog CSV files into $DATABASE."

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" "$DATABASE" <<'SQL'
SET autocommit = 0;
SET unique_checks = 0;
SET foreign_key_checks = 0;

LOAD DATA INFILE '/var/lib/mysql-files/dev-data/brands.csv'
INTO TABLE brands
CHARACTER SET utf8mb4
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"' ESCAPED BY '"'
LINES TERMINATED BY '\n'
(id, created_at, updated_at, name, status)
SET deleted_at = NULL;

LOAD DATA INFILE '/var/lib/mysql-files/dev-data/products.csv'
INTO TABLE products
CHARACTER SET utf8mb4
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"' ESCAPED BY '"'
LINES TERMINATED BY '\n'
(id, created_at, updated_at, brand_id, name, price, status)
SET deleted_at = NULL;

LOAD DATA INFILE '/var/lib/mysql-files/dev-data/product_stocks.csv'
INTO TABLE product_stocks
CHARACTER SET utf8mb4
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"' ESCAPED BY '"'
LINES TERMINATED BY '\n'
(id, created_at, updated_at, product_id, reserved_quantity, stock_quantity)
SET deleted_at = NULL;

LOAD DATA INFILE '/var/lib/mysql-files/dev-data/product_stats.csv'
INTO TABLE product_stats
CHARACTER SET utf8mb4
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"' ESCAPED BY '"'
LINES TERMINATED BY '\n'
(id, created_at, updated_at, like_count, product_id)
SET deleted_at = NULL;

SET foreign_key_checks = 1;
SET unique_checks = 1;
COMMIT;
SQL

echo "Generated catalog CSV load complete."
