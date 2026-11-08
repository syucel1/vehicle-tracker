# Build context: repository root  (docker build -f services/ingest-bridge/Dockerfile .)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY libs libs
COPY services services
COPY tools tools
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl services/ingest-bridge -am -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/services/ingest-bridge/target/ingest-bridge.jar app.jar
USER app
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8081 && printf 'GET /actuator/health/readiness HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q '\"UP\"' <&3"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
