
-- =====================================================
-- V1 - INITIAL ORDER SERVICE DATABASE SCHEMA
-- Database: MySQL 8
--
-- Schema ownership: Flyway
-- Hibernate responsibility: validate mappings
-- =====================================================

-- -----------------------------------------------------
-- 1. ORDERS
-- -----------------------------------------------------

CREATE TABLE orders (
                        id BINARY(16) NOT NULL,
                        version BIGINT NOT NULL,
                        user_id BINARY(16),
                        product_id BINARY(16),
                        quantity INTEGER NOT NULL,
                        price DECIMAL(38, 2),
                        status ENUM(
        'CANCELLED',
        'COMPLETED',
        'CREATED'
    ),
                        PRIMARY KEY (id)
) ENGINE = InnoDB;


-- -----------------------------------------------------
-- 2. OUTBOX EVENTS
-- -----------------------------------------------------

CREATE TABLE outbox_event (
                              id BINARY(16) NOT NULL,
                              event_id BINARY(16) NOT NULL,
                              aggregate_id BINARY(16) NOT NULL,

                              topic VARCHAR(255) NOT NULL,
                              event_type VARCHAR(255) NOT NULL,
                              payload LONGTEXT NOT NULL,

                              status ENUM(
        'IN_PROGRESS',
        'PENDING',
        'PUBLISHED'
    ) NOT NULL,

                              created_at DATETIME(6) NOT NULL,
                              published_at DATETIME(6),

                              attempt_count INTEGER NOT NULL,

                              claimed_by BINARY(16),
                              lease_expires_at DATETIME(6),
                              claim_token BINARY(16),

                              PRIMARY KEY (id),

                              CONSTRAINT uk_outbox_event_event_id
                                  UNIQUE (event_id)
) ENGINE = InnoDB;


-- -----------------------------------------------------
-- 3. OUTBOX INDEXES
-- -----------------------------------------------------

CREATE INDEX idx_outbox_status_created
    ON outbox_event (status, created_at);

CREATE INDEX idx_outbox_status_lease_id
    ON outbox_event (
                     status,
                     lease_expires_at,
                     id
        );


-- -----------------------------------------------------
-- 4. PROCESSED EVENTS
-- -----------------------------------------------------

CREATE TABLE processed_event (
                                 event_id BINARY(16) NOT NULL,
                                 processed_at DATETIME(6),
                                 PRIMARY KEY (event_id)
) ENGINE = InnoDB;
