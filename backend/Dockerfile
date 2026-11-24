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

# Memory footprint. Railway bills RAM per minute, so the JVM gets a small fixed heap instead of 75 % of the host.
# - Xmx384m covers four concurrent 12 MP uploads plus their WebP derivatives (load-tested).
# - G1 periodic GC + low heap free ratios + native heap trimming hand memory back to the OS after an admin upload
#   spike; SerialGC idles lower but keeps the spike forever.
# - C1-only JIT: the store's CPU idles near 0, so peak throughput is not worth the C2 compiler's memory.
# - MALLOC_ARENA_MAX stops glibc from keeping one malloc arena per thread.
# Measured under the same workload: ~355 MiB after idling vs ~840 MiB with -XX:MaxRAMPercentage=75.
# Railway/compose can override JAVA_TOOL_OPTIONS; keep them in step (backend/railway.env.example).
ENV MALLOC_ARENA_MAX=2 \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -Xms64m -Xmx384m -XX:G1PeriodicGCInterval=30000 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 -XX:TieredStopAtLevel=1 -XX:+UnlockExperimentalVMOptions -XX:TrimNativeHeapInterval=30000 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["java", "-jar", "/app/application.jar"]
