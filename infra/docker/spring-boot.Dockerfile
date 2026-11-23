# syntax=docker/dockerfile:1
# Image générique des services Spring Boot (BF-002-07).
# Le contexte de build est le dossier du service : services/<service>.

FROM eclipse-temurin:27-jdk@sha256:5e6d9093c7a8e95adfaa8828253c58f2c6820116d07a062440a778358807578a AS build
WORKDIR /build
COPY . .
# Le cache évite de retélécharger les dépendances Maven à chaque build
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -ntp package -Dmaven.test.skip=true \
    && cp target/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --destination /extracted

FROM eclipse-temurin:27-jre@sha256:acfb350b50e707addf3dcbb47f7052d1882d1e5f1670ce838a9c7838d82d4fe5
RUN groupadd --system --gid 10001 bankflow \
    && useradd --system --uid 10001 --gid bankflow --no-create-home bankflow
WORKDIR /app
# Une couche par type de contenu : seule l'application change d'un build à l'autre
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
COPY <<'SCRIPT' /app/healthcheck.sh
#!/bin/bash
# L'image ne contient ni curl ni wget : la requête passe par le TCP intégré à bash
exec 3<>"/dev/tcp/127.0.0.1/${SERVER_PORT:-8080}" || exit 1
printf 'GET /actuator/health/readiness HTTP/1.0\r\nHost: localhost\r\n\r\n' >&3
grep -q '"status":"UP"' <&3
SCRIPT
USER bankflow
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 CMD ["bash", "/app/healthcheck.sh"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
