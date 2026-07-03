CREATE TABLE example (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    description VARCHAR(255) NULL,
    name VARCHAR(255) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    birthdate DATE NOT NULL,
    email VARCHAR(100) NOT NULL,
    encrypted_password VARCHAR(255) NOT NULL,
    login_id VARCHAR(20) NOT NULL,
    name VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_login_id (login_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE brands (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    brand_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    price BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_products_brand_id (brand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE product_detail_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    product_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE product_stocks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    product_id BIGINT NOT NULL,
    reserved_quantity INT NOT NULL,
    stock_quantity INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_stocks_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE product_stats (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    like_count BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_stats_product_id (product_id),
    KEY idx_product_stats_deleted_like_count_product_id (deleted_at, like_count DESC, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE carts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_carts_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE cart_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_items_cart_product (cart_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE coupons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    expired_at DATETIME(6) NOT NULL,
    issue_type VARCHAR(40) NOT NULL,
    min_order_amount BIGINT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    `value` BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    name VARCHAR(100) NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE event_coupons (
    issued_quantity BIGINT NOT NULL,
    total_quantity BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    PRIMARY KEY (coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE issued_coupons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    coupon_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_issued_coupons_user_coupon (user_id, coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE product_like_histories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    action VARCHAR(20) NOT NULL,
    product_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_product_like_histories_user_product_created_id (user_id, product_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE product_like_cursors (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    last_history_id BIGINT NULL,
    product_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_like_cursors_user_product (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    cancel_reason VARCHAR(30) NULL,
    coupon_id BIGINT NULL,
    delivery_address VARCHAR(500) NOT NULL,
    delivery_request VARCHAR(500) NOT NULL,
    discount_amount BIGINT NOT NULL,
    payment_amount BIGINT NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    reservation_expires_at DATETIME(6) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_amount BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    brand_name_snapshot VARCHAR(150) NOT NULL,
    order_id BIGINT NOT NULL,
    price_snapshot BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name_snapshot VARCHAR(150) NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE stock_reservations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    approved_amount BIGINT NULL,
    approved_at DATETIME(6) NULL,
    canceled_at DATETIME(6) NULL,
    completion_retry_count INT NOT NULL,
    failure_reason VARCHAR(500) NULL,
    last_failed_at DATETIME(6) NULL,
    order_id BIGINT NOT NULL,
    payment_key VARCHAR(100) NULL,
    payment_request_id VARCHAR(100) NOT NULL,
    pg_provider VARCHAR(30) NOT NULL,
    pg_transaction_id VARCHAR(100) NULL,
    requested_amount BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_order_id (order_id),
    UNIQUE KEY uk_payments_provider_payment_request_id (pg_provider, payment_request_id),
    UNIQUE KEY uk_payments_provider_payment_key (pg_provider, payment_key),
    UNIQUE KEY uk_payments_provider_pg_transaction_id (pg_provider, pg_transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE payment_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    approved_amount BIGINT NULL,
    event_type VARCHAR(40) NOT NULL,
    failure_reason VARCHAR(500) NULL,
    order_id BIGINT NOT NULL,
    payment_id BIGINT NULL,
    payment_key VARCHAR(100) NULL,
    payment_request_id VARCHAR(100) NOT NULL,
    pg_provider VARCHAR(30) NOT NULL,
    pg_status VARCHAR(50) NULL,
    pg_transaction_id VARCHAR(100) NULL,
    raw_response_summary VARCHAR(1000) NULL,
    requested_amount BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
