FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/order-api-0.0.1-SNAPSHOT.jar order-api.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "order-api.jar"]
