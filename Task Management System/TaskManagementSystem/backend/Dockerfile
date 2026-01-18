# syntax=docker/dockerfile:1

# Use Gradle image to avoid Gradle wrapper distribution download timeouts.
FROM gradle:9.2.1-jdk21 AS build
WORKDIR /home/gradle/project

# Help prevent flaky network timeouts when downloading dependencies.
ENV GRADLE_OPTS="-Dorg.gradle.internal.http.connectionTimeout=60000 -Dorg.gradle.internal.http.socketTimeout=60000"

# Copy build files first for better layer caching
COPY --chown=gradle:gradle build.gradle settings.gradle ./

# Copy source
COPY --chown=gradle:gradle src ./src

# Build runnable jar
RUN gradle --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /home/gradle/project/build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java","-jar","/app/app.jar"]
