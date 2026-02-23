#!/bin/bash
set -e

echo "Waiting for Kafka to be ready..."

# Wait for Kafka to be ready using Confluent Utilities
cub kafka-ready -b kafka:9092 1 30

echo "Creating Kafka topics..."

# Topic definitions (matching shared-events Topics constants)
ORDER_EVENTS_V1="order.events.v1"
ORDER_EVENTS_V1_DLT="order.events.v1.DLT"
PAYMENT_EVENTS_V1="payment.events.v1"
INVENTORY_EVENTS_V1="inventory.events.v1"
NOTIFICATION_EVENTS_V1="notification.events.v1"

# Common configuration
REPLICATION_FACTOR=1
PARTITIONS=3
ORDER_RETENTION_MS=604800000    # 7 days
ORDER_DLT_RETENTION_MS=1209600000 # 14 days

# Create main topics
kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic "$ORDER_EVENTS_V1" \
  --replication-factor $REPLICATION_FACTOR \
  --partitions $PARTITIONS \
  --config retention.ms=$ORDER_RETENTION_MS

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic "$ORDER_EVENTS_V1_DLT" \
  --replication-factor $REPLICATION_FACTOR \
  --partitions $PARTITIONS \
  --config retention.ms=$ORDER_DLT_RETENTION_MS

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic "$PAYMENT_EVENTS_V1" \
  --replication-factor $REPLICATION_FACTOR \
  --partitions $PARTITIONS

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic "$INVENTORY_EVENTS_V1" \
  --replication-factor $REPLICATION_FACTOR \
  --partitions $PARTITIONS

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic "$NOTIFICATION_EVENTS_V1" \
  --replication-factor $REPLICATION_FACTOR \
  --partitions $PARTITIONS

echo "All Kafka topics created successfully!"