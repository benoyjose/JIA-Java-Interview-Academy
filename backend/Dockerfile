FROM gradle:9.1.0-jdk25 AS build
WORKDIR /workspace
COPY build.gradle .
COPY src ./src
RUN gradle --no-daemon clean bootJar

FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system jia \
    && useradd --system --gid jia --home-dir /app jia
WORKDIR /app
COPY --from=build --chown=jia:jia /workspace/build/libs/*.jar /app/jia-api.jar
USER jia
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=5 \
  CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/jia-api.jar"]
