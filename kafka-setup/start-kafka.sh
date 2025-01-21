#!/bin/bash
echo "Starting Zookeeper..."
./kafka-3.9/bin/zookeeper-server-start.sh ./kafka-3.9/config/zookeeper.properties &
sleep 5
echo "Starting Kafka Broker..."
./kafka-3.9/bin/kafka-server-start.sh ./kafka-3.9/config/server.properties &
