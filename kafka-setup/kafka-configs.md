# Kafka Configuration and Microservice Communication

This document provides an overview of Kafka setup, commands for starting Kafka and Zookeeper, and the relationships between the **API Gateway**, **Order Service**, **Payment Service**, and **Notification Service**.

---

## Kafka Setup and Configuration

### Prerequisites
- **Java 11+** installed.
- **Kafka binaries** downloaded (e.g., [Apache Kafka](https://kafka.apache.org/downloads)).
- Update the `server.properties` file for Kafka and `zookeeper.properties` for Zookeeper as required.

### Configuring Zookeeper
1. Navigate to the Kafka config folder:
   ```bash
   cd kafka-implementation/config
   ```
2. Update `zookeeper.properties` if needed:
    - Default port: `2181`
    - Example configuration:
      ```properties
      dataDir=/tmp/zookeeper
      clientPort=2181
      maxClientCnxns=60
      ```

### Configuring Kafka Broker
1. Update `server.properties`:
    - Set a unique `broker.id` for each Kafka broker in the cluster.
    - Default configurations:
      ```properties
      broker.id=0
      log.dirs=/tmp/kafka-logs
      zookeeper.connect=localhost:2181
      ```
    - For multi-broker setup, modify `broker.id` and `log.dirs`.

---

## Commands to Start Zookeeper and Kafka

### 1. Start Zookeeper
   ```bash
   kafka-implementation/bin/zookeeper-server-start.sh <kafka_folder>/config/zookeeper.properties
   ```

### 2. Start Kafka Broker
   ```bash
   kafka-implementation/bin/kafka-server-start.sh <kafka_folder>/config/server.properties
   ```

### 3. Create a Kafka Topic
   ```bash
   kafka-implementation/bin/kafka-topics.sh --create --topic <topic_name> --bootstrap-server localhost:9092 --partitions <num_partitions> --replication-factor <replication_factor>
   ```
Example:
   ```bash
   kafka-implementation/bin/kafka-topics.sh --create --topic orders --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
   ```

### 4. List Topics
   ```bash
   kafka-implementation/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
   ```

### 5. Consume Messages from a Topic
   ```bash
   kafka-implementation/bin/kafka-console-consumer.sh --topic <topic_name> --bootstrap-server localhost:9092 --from-beginning
   ```

### 6. Produce Messages to a Topic
   ```bash
   kafka-implementation/bin/kafka-console-producer.sh --topic <topic_name> --bootstrap-server localhost:9092
   ```

---

## Service Relationships in the Event-Driven Architecture

The following illustrates the interaction between the **API Gateway**, **Order Service**, **Payment Service**, and **Notification Service** in a Kafka-based system.

### Architecture Diagram
```plaintext
                    +------------------+
                    |  API Gateway     |
                    +--------+---------+
                             |
        +----------------------------------------------+
        |                                              |
+---------------+                              +----------------+
| Order Service |  --->    Kafka Events  --->  | Payment Service|
+---------------+                              +----------------+
        |                                              |
        +----------------------------------------------+
                             |
                    +----------------------+
                    | Notification Service |
                    +----------------------+
```

### Communication Flow
1. **API Gateway**:
    - Handles client requests and routes them to the appropriate microservices.
    - Example: Routes order creation requests to the **Order Service**.

2. **Order Service**:
    - Publishes events (e.g., `OrderCreated`) to a Kafka topic (e.g., `orders`).
    - Topics used: `orders`.

3. **Payment Service**:
    - Listens to Kafka topics (e.g., `orders`) for order events.
    - Processes payment and publishes `PaymentProcessed` events to another Kafka topic (e.g., `payments`).

4. **Notification Service**:
    - Listens to `orders` and `payments` topics.
    - Sends notifications (e.g., order confirmation, payment success).

### Kafka Topics
- `orders`: Used for order creation events.
- `payments`: Used for payment-related events.
- `notifications`: Used for notifications triggered by other services.

---

## Conclusion

This setup and configuration provide the foundation for a Kafka-based event-driven microservice system, enabling efficient communication between the **API Gateway**, **Order Service**, **Payment Service**, and **Notification Service**.
