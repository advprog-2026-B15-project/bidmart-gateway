FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built jar from gradle build stage (run in GitHub Actions runner)
COPY build/libs/gateway-*.jar app.jar

# Expose default Spring Boot Port
EXPOSE 8088

ENTRYPOINT ["java", "-jar", "app.jar"]
