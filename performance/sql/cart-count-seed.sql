SET @now = TIMESTAMP('2026-06-19 00:00:00.000000');
SET @password = '$2y$10$xVrGV6VuT6Cz4AqB5meSVe8op9B1Zu6rDJKmi0DayGun1fMqn3tdu';

DELETE FROM cart_items WHERE cart_id BETWEEN 10001 AND 11000;
DELETE FROM carts WHERE id BETWEEN 10001 AND 11000;
DELETE FROM users WHERE id BETWEEN 10001 AND 11000 OR login_id REGEXP '^cartuser[0-9]{4}$';

INSERT INTO users (id, created_at, updated_at, deleted_at, login_id, encrypted_password, name, birthdate, email, role)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 1000
)
SELECT
    10000 + n,
    @now,
    @now,
    NULL,
    CONCAT('cartuser', LPAD(n, 4, '0')),
    @password,
    CONCAT('Cart User ', LPAD(n, 4, '0')),
    DATE('1990-01-01'),
    CONCAT('cartuser', LPAD(n, 4, '0'), '@example.com'),
    'CONSUMER'
FROM seq;

INSERT INTO carts (id, created_at, updated_at, deleted_at, user_id)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 1000
)
SELECT
    10000 + n,
    @now,
    @now,
    NULL,
    10000 + n
FROM seq;

INSERT INTO cart_items (id, created_at, updated_at, deleted_at, cart_id, product_id, quantity)
WITH RECURSIVE users_seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM users_seq WHERE n < 1000
), item_seq(i) AS (
    SELECT 1
    UNION ALL
    SELECT i + 1 FROM item_seq WHERE i < 10
)
SELECT
    ((10000 + n) * 100) + i,
    @now,
    @now,
    NULL,
    10000 + n,
    100000 + i,
    1
FROM users_seq
CROSS JOIN item_seq;

SELECT
    (SELECT COUNT(*) FROM users WHERE login_id REGEXP '^cartuser[0-9]{4}$') AS seeded_users,
    (SELECT COUNT(*) FROM carts WHERE id BETWEEN 10001 AND 11000) AS seeded_carts,
    (SELECT COUNT(*) FROM cart_items WHERE cart_id BETWEEN 10001 AND 11000) AS seeded_cart_items;
