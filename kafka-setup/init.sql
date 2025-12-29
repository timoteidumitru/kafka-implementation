CREATE DATABASE IF NOT EXISTS kafka_order_db;
CREATE DATABASE IF NOT EXISTS kafka_payment_db;
CREATE DATABASE IF NOT EXISTS kafka_inventory_db;
CREATE DATABASE IF NOT EXISTS kafka_notification_db;

-- Optional: Grant privileges to the user on all databases
GRANT ALL PRIVILEGES ON kafka_order_db.* TO 'admin'@'%';
GRANT ALL PRIVILEGES ON kafka_payment_db.* TO 'admin'@'%';
GRANT ALL PRIVILEGES ON kafka_inventory_db.* TO 'admin'@'%';
GRANT ALL PRIVILEGES ON kafka_notification_db.* TO 'admin'@'%';
FLUSH PRIVILEGES;
