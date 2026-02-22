# Use Java 17
FROM eclipse-temurin:17-jre-alpine
# Create a folder for the app
WORKDIR /app
# Copy the JAR from the target folder to the container
COPY target/*.jar app.jar
# Command to run the bot
ENTRYPOINT ["java", "-jar", "app.jar"]
