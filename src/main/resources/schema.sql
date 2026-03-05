-- 결제 시스템 스키마 정의
-- ddl-auto: none 설정으로 Hibernate가 DDL을 직접 실행하지 않으므로 이 파일로 직접 관리한다.
-- 초기화 필요 시: docker-compose down -v && docker-compose up -d 후 앱 재시작

CREATE TABLE IF NOT EXISTS products
(
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    product_id VARCHAR(36)    NOT NULL,
    name       VARCHAR(255)   NOT NULL,
    price      DECIMAL(15, 2) NOT NULL,
    stock      INT            NOT NULL,
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at DATETIME(6)    NOT NULL,
    updated_at DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_id (product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS orders
(
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    order_id      VARCHAR(36)    NOT NULL,
    product_id    BIGINT         NOT NULL,
    quantity      INT            NOT NULL,
    total_amount  DECIMAL(15, 2) NOT NULL,
    status        VARCHAR(50)    NOT NULL,
    customer_name VARCHAR(255)   NOT NULL,
    version       BIGINT         NOT NULL DEFAULT 0,
    created_at    DATETIME(6)    NOT NULL,
    updated_at    DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_id (order_id),
    CONSTRAINT fk_orders_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS payments
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    payment_key     VARCHAR(36)    NOT NULL,
    order_id        BIGINT         NOT NULL,
    amount          DECIMAL(15, 2) NOT NULL,
    status          VARCHAR(50)    NOT NULL,
    payment_method  VARCHAR(50)    NOT NULL,
    idempotency_key VARCHAR(255)   NOT NULL,
    cancel_reason   VARCHAR(500)   NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_key (payment_key),
    UNIQUE KEY uk_idempotency_key (idempotency_key),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
