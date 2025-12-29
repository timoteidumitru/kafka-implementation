FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/payment-api-0.0.1-SNAPSHOT.jar payment-api.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "payment-api.jar"]
