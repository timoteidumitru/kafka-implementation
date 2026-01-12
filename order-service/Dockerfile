FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

COPY target/order-service-1.0.0.jar order-service.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "order-service.jar"]
