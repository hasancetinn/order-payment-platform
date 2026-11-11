# One Dockerfile for every service:  docker build --build-arg MODULE=order-service -t order-service .
# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/${MODULE}-*-SNAPSHOT.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
