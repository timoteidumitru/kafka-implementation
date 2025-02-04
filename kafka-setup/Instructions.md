
# Kafka Setup Using Docker

This guide explains how to start Kafka and Zookeeper using Docker Compose.

## Prerequisites

- **Docker** must be installed and running on your machine. If you haven't already, you can download it from [Docker's official website](https://www.docker.com/).

## Steps to Start Kafka and Zookeeper

1. **Clone or Download the Project**
   Navigate to the project directory where your `docker-compose.yml` file is located.

2. **Start Kafka and Zookeeper Using Docker Compose**

   Run the following command to start both Kafka and Zookeeper services:

   ```bash
   docker-compose up
   ```

   This will start the services in the foreground and display logs.

3. **Run in Detached Mode (Optional)**

   If you prefer to run the services in the background, use the `-d` flag:

   ```bash
   docker-compose up -d
   ```

4. **Verify the Services**

   To ensure the containers are running, use the following command:

   ```bash
   docker ps
   ```

   This should show `zookeeper` and `kafka` containers running on their respective ports.

5. **Access Kafka**

   Kafka will be accessible on `localhost:9092`. You can now connect to Kafka using your desired Kafka client.

### Commands for kafka topics creation

```dockerfile
+ Order topic
kafka-topics.sh --create --topic order-topic --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

+ Payment Topic
kafka-topics.sh --create --topic payment-result-topic --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

+ Inventory topic
kafka-topics.sh --create --topic inventory-update-topic --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

```

## Stopping the Services

To stop the services, run the following command:

```bash
docker-compose down
```

This will stop and remove the containers.

---

For more information, refer to the official [Kafka documentation](https://kafka.apache.org/documentation/).
