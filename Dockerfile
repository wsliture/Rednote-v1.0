ARG REDNOTE_IMAGE_PREFIX=""
FROM ${REDNOTE_IMAGE_PREFIX}maven:3.9.9-eclipse-temurin-17 AS build
ENV MAVEN_OPTS="-Xmx384m"
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -Dmaven.test.skip=true package

FROM ${REDNOTE_IMAGE_PREFIX}eclipse-temurin:17.0.18_8-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 rednote \
    && useradd --uid 10001 --gid rednote --no-create-home rednote \
    && mkdir -p /app /var/lib/rednote/uploads /var/log/rednote \
    && chown -R rednote:rednote /app /var/lib/rednote /var/log/rednote
WORKDIR /app
COPY --from=build --chown=rednote:rednote /build/target/rednote-1.0.0-SNAPSHOT.jar /app/rednote.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=120s --retries=6 \
    CMD curl --fail --silent --max-time 4 http://127.0.0.1:8080/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/rednote.jar"]
