FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode dependency:go-offline

COPY src src
RUN ./mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends gosu \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring spring \
    && mkdir -p /app/uploads \
    && chown spring:spring /app/uploads

COPY --from=build /app/target/computer-store-api-*.jar application.jar
COPY --chmod=755 docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["java", "-jar", "/app/application.jar"]
