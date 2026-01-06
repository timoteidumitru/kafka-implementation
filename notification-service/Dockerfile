FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/notification-service-0.0.1-SNAPSHOT.jar notification-service.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "notification-service.jar"]
