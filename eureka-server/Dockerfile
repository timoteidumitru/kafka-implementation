# Use OpenJDK 17 as base image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy JAR file into the container
COPY target/eureka-server-0.0.1-SNAPSHOT.jar eureka-server.jar

# Expose Eureka Server port
EXPOSE 8761

# Run the application
ENTRYPOINT ["java", "-jar", "eureka-server.jar"]
