# syntax=docker/dockerfile:1

# Multi-stage: build with a full JDK, run on a slim JRE - the build toolchain (Maven, the
# compiler) never ships in the runtime image. --mount=type=cache on both RUN steps reuses the
# host's Maven download cache across builds (BuildKit only - `docker build` on a recent Docker
# Desktop has this on by default) so iterating on this file doesn't re-download every dependency
# each time; it does NOT get baked into either image layer, so this has zero effect on final
# image size or on a real CI runner starting with an empty cache (just a slower first build there).
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -B
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/betedge-*.jar app.jar
EXPOSE 8080
# JAVA_OPTS is empty by default (JVM falls back to its own cgroup-aware auto-sizing) - the prod
# compose file sets it explicitly (-Xmx/-Xms) per the Paso 2 memory budget. `exec` so java
# replaces the shell as PID 1 and receives SIGTERM directly (clean shutdown) instead of the shell
# swallowing it.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
