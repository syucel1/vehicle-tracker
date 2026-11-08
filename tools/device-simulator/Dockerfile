# Build context: repository root  (docker build -f tools/device-simulator/Dockerfile .)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY libs libs
COPY services services
COPY tools tools
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl tools/device-simulator -am -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
COPY --from=build /src/tools/device-simulator/target/device-simulator.jar /app/device-simulator.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/device-simulator.jar"]
CMD ["--help"]
