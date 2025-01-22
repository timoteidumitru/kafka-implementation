### Commands for kafka topics creation

```dockerfile
+ Order topic
kafka-topics.sh --create --topic order-topic --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

+ Payment Topic
kafka-topics.sh --create --topic payment-result-topic --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

+ Inventory topic
kafka-topics.sh --create --topic inventory-update-topic --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

```