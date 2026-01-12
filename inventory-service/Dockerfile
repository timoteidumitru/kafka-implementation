FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

COPY target/inventory-service-1.0.0.jar inventory-service.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "inventory-service.jar"]
