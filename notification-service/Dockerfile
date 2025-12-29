FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/notification-api-0.0.1-SNAPSHOT.jar notification-api.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "notification-api.jar"]
