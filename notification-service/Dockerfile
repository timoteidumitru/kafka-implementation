FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

COPY target/notification-service-1.0.0.jar inventory-service.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "inventory-service.jar"]
