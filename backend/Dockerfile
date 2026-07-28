FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode dependency:go-offline

COPY src src
RUN ./mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
RUN addgroup --system spring && adduser --system --ingroup spring spring

COPY --from=build /app/target/computer-store-api-*.jar application.jar

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
