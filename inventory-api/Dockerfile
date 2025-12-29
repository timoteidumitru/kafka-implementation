FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/inventory-api-0.0.1-SNAPSHOT.jar inventory-api.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "inventory-api.jar"]
