FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

COPY target/payment-service-1.0.0.jar payment-service.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "payment-service.jar"]
