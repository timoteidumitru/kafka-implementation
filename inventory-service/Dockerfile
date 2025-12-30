FROM openjdk:21-jdk-slim

WORKDIR /app

COPY target/inventory-service-0.0.1-SNAPSHOT.jar inventory-service.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "inventory-service.jar"]
