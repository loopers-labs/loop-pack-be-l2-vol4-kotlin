SET @now = TIMESTAMP('2026-06-19 00:00:00.000000');
SET @password = '$2y$10$xVrGV6VuT6Cz4AqB5meSVe8op9B1Zu6rDJKmi0DayGun1fMqn3tdu';

INSERT INTO example (id, created_at, updated_at, deleted_at, name, description) VALUES
    (1, @now, @now, NULL, 'local example', 'Seed row for local development');

INSERT INTO users (id, created_at, updated_at, deleted_at, login_id, encrypted_password, name, birthdate, email, role) VALUES
    (1, @now, @now, NULL, 'loopers01', @password, 'Loopers User 01', '1990-01-01', 'loopers01@example.com', 'CONSUMER'),
    (2, @now, @now, NULL, 'loopers02', @password, 'Loopers User 02', '1992-02-02', 'loopers02@example.com', 'CONSUMER'),
    (3, @now, @now, NULL, 'admin01', @password, 'Loopers Admin', '1988-03-03', 'admin01@example.com', 'ADMIN');

INSERT INTO brands (id, created_at, updated_at, deleted_at, name, status) VALUES
    (1, @now, @now, NULL, 'Loopers Apparel', 'ACTIVE'),
    (2, @now, @now, NULL, 'Daily Goods', 'ACTIVE'),
    (3, @now, @now, NULL, 'Archived Brand', 'INACTIVE');

INSERT INTO products (id, created_at, updated_at, deleted_at, brand_id, name, price, status) VALUES
    (1, @now, @now, NULL, 1, 'Loopers Hoodie', 59000, 'ON_SALE'),
    (2, @now, @now, NULL, 1, 'Loopers T-Shirt', 29000, 'ON_SALE'),
    (3, @now, @now, NULL, 2, 'Daily Mug', 15000, 'ON_SALE'),
    (4, @now, @now, NULL, 3, 'Suspended Cap', 25000, 'SUSPENDED');

INSERT INTO product_stocks (id, created_at, updated_at, deleted_at, product_id, stock_quantity, reserved_quantity) VALUES
    (1, @now, @now, NULL, 1, 100, 2),
    (2, @now, @now, NULL, 2, 50, 0),
    (3, @now, @now, NULL, 3, 20, 1),
    (4, @now, @now, NULL, 4, 0, 0);

INSERT INTO product_stats (id, created_at, updated_at, deleted_at, product_id, like_count) VALUES
    (1, @now, @now, NULL, 1, 2),
    (2, @now, @now, NULL, 2, 1),
    (3, @now, @now, NULL, 3, 0),
    (4, @now, @now, NULL, 4, 0);

INSERT INTO product_detail_images (id, created_at, updated_at, deleted_at, product_id, image_url, sort_order) VALUES
    (1, @now, @now, NULL, 1, 'https://static.loopers.local/products/1/main.jpg', 0),
    (2, @now, @now, NULL, 1, 'https://static.loopers.local/products/1/detail.jpg', 1),
    (3, @now, @now, NULL, 2, 'https://static.loopers.local/products/2/main.jpg', 0),
    (4, @now, @now, NULL, 3, 'https://static.loopers.local/products/3/main.jpg', 0);

INSERT INTO carts (id, created_at, updated_at, deleted_at, user_id) VALUES
    (1, @now, @now, NULL, 1),
    (2, @now, @now, NULL, 2);

INSERT INTO cart_items (id, created_at, updated_at, deleted_at, cart_id, product_id, quantity) VALUES
    (1, @now, @now, NULL, 1, 1, 1),
    (2, @now, @now, NULL, 1, 3, 2),
    (3, @now, @now, NULL, 2, 2, 1);

INSERT INTO coupons (id, created_at, updated_at, deleted_at, name, type, `value`, min_order_amount, expired_at, issue_type) VALUES
    (1, @now, @now, NULL, '5000 KRW off over 50000', 'FIXED', 5000, 50000, '2027-12-31 23:59:59.000000', 'YEAR_ROUND'),
    (2, @now, @now, NULL, '10 percent off', 'RATE', 10, 30000, '2027-12-31 23:59:59.000000', 'YEAR_ROUND'),
    (3, @now, @now, NULL, 'Expired local coupon', 'FIXED', 3000, NULL, '2025-12-31 23:59:59.000000', 'YEAR_ROUND');

INSERT INTO issued_coupons (id, created_at, updated_at, deleted_at, user_id, coupon_id, status) VALUES
    (1, @now, @now, NULL, 1, 1, 'AVAILABLE'),
    (2, @now, @now, NULL, 1, 2, 'AVAILABLE'),
    (3, @now, @now, NULL, 2, 1, 'USED'),
    (4, @now, @now, NULL, 2, 3, 'AVAILABLE');

INSERT INTO product_like_histories (id, created_at, updated_at, deleted_at, user_id, product_id, action) VALUES
    (1, @now, @now, NULL, 1, 1, 'REGISTER'),
    (2, @now, @now, NULL, 2, 1, 'REGISTER'),
    (3, @now, @now, NULL, 1, 2, 'REGISTER'),
    (4, @now, @now, NULL, 2, 2, 'REGISTER'),
    (5, @now, @now, NULL, 2, 2, 'CANCEL');

INSERT INTO product_like_cursors (id, created_at, updated_at, deleted_at, user_id, product_id, last_history_id) VALUES
    (1, @now, @now, NULL, 1, 1, 1),
    (2, @now, @now, NULL, 2, 1, 2),
    (3, @now, @now, NULL, 1, 2, 3),
    (4, @now, @now, NULL, 2, 2, 5);

INSERT INTO orders (
    id, created_at, updated_at, deleted_at, user_id, reservation_expires_at, delivery_address, delivery_request,
    phone_number, coupon_id, total_amount, discount_amount, payment_amount, status, cancel_reason
) VALUES
    (1, @now, @now, NULL, 1, '2027-01-01 00:10:00.000000', 'Seoul Gangnam-gu 1', 'Leave at door', '010-1111-1111', 1, 59000, 5000, 54000, 'PAYMENT_PENDING', NULL),
    (2, @now, @now, NULL, 2, '2027-01-01 00:10:00.000000', 'Seoul Mapo-gu 2', 'Call on arrival', '010-2222-2222', NULL, 29000, 0, 29000, 'COMPLETED', NULL),
    (3, @now, @now, NULL, 1, '2026-01-01 00:10:00.000000', 'Seoul Songpa-gu 3', 'No request', '010-3333-3333', NULL, 15000, 0, 15000, 'FAILED', NULL);

INSERT INTO order_items (
    id, created_at, updated_at, deleted_at, order_id, product_id, product_name_snapshot, brand_name_snapshot, price_snapshot, quantity
) VALUES
    (1, @now, @now, NULL, 1, 1, 'Loopers Hoodie', 'Loopers Apparel', 59000, 1),
    (2, @now, @now, NULL, 2, 2, 'Loopers T-Shirt', 'Loopers Apparel', 29000, 1),
    (3, @now, @now, NULL, 3, 3, 'Daily Mug', 'Daily Goods', 15000, 1);

INSERT INTO stock_reservations (id, created_at, updated_at, deleted_at, order_id, product_id, quantity, status) VALUES
    (1, @now, @now, NULL, 1, 1, 1, 'IN_PROGRESS'),
    (2, @now, @now, NULL, 2, 2, 1, 'COMPLETED'),
    (3, @now, @now, NULL, 3, 3, 1, 'IN_PROGRESS');

INSERT INTO payments (
    id, created_at, updated_at, deleted_at, order_id, status, pg_provider, payment_request_id, requested_amount,
    payment_key, pg_transaction_id, approved_amount, failure_reason, completion_retry_count, approved_at, canceled_at, last_failed_at
) VALUES
    (1, @now, @now, NULL, 1, 'READY', 'FAKE', 'local-payment-request-1', 54000, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL),
    (2, @now, @now, NULL, 2, 'APPROVED', 'FAKE', 'local-payment-request-2', 29000, 'local-payment-key-2', 'local-pg-tx-2', 29000, NULL, 0, @now, NULL, NULL),
    (3, @now, @now, NULL, 3, 'COMPLETION_FAILED', 'FAKE', 'local-payment-request-3', 15000, 'local-payment-key-3', 'local-pg-tx-3', 15000, 'Local seed completion failure', 1, @now, NULL, @now);

INSERT INTO payment_events (
    id, created_at, updated_at, deleted_at, order_id, payment_id, event_type, pg_provider, payment_request_id,
    payment_key, pg_transaction_id, requested_amount, approved_amount, pg_status, failure_reason, raw_response_summary
) VALUES
    (1, @now, @now, NULL, 1, 1, 'REQUEST_CREATED', 'FAKE', 'local-payment-request-1', NULL, NULL, 54000, NULL, 'READY', NULL, 'Local pending payment request'),
    (2, @now, @now, NULL, 2, 2, 'APPROVE_SUCCEEDED', 'FAKE', 'local-payment-request-2', 'local-payment-key-2', 'local-pg-tx-2', 29000, 29000, 'APPROVED', NULL, 'Local approved payment'),
    (3, @now, @now, NULL, 3, 3, 'COMPLETION_FAILED', 'FAKE', 'local-payment-request-3', 'local-payment-key-3', 'local-pg-tx-3', 15000, 15000, 'APPROVED', 'Local seed completion failure', 'Local payment completion failed after PG approval');
