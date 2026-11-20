ARG TEMURIN_VERSION=21.0.13_9

FROM --platform=$BUILDPLATFORM eclipse-temurin:${TEMURIN_VERSION}-jdk AS build
WORKDIR /workspace


COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon > /dev/null

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test \
    && cp "$(ls build/libs/*.jar | grep -v plain)" app.jar



FROM eclipse-temurin:${TEMURIN_VERSION}-jre
WORKDIR /app


RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /var/log/app && chown app:app /var/log/app
COPY --from=build --chown=app:app /workspace/app.jar app.jar

USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]