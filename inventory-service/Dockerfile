# Use JDK 21
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# Copy the fat jar built by Maven
COPY target/inventory-service-1.0.0.jar inventory-service.jar

# Expose port
EXPOSE 8084

# Entry point
ENTRYPOINT ["java", "-jar", "inventory-service.jar", "--spring.profiles.active=docker"]
