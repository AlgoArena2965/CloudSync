FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Download dependencies (layer caching)
RUN ./mvnw dependency:go-offline -B

# Copy source code and build
COPY src src
COPY pom.xml pom.xml

RUN ./mvnw package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache \
    bash \
    curl \
    && addgroup -S cloudsync \
    && adduser -S cloudsync -G cloudsync

WORKDIR /app

# Create directories for uploads and logs
RUN mkdir -p /tmp/cloudsync-uploads /var/log/cloudsync && \
    chown -R cloudsync:cloudsync /tmp/cloudsync-uploads /var/log/cloudsync

# Copy the built JAR
COPY --from=builder /app/target/*.jar app.jar

# Set ownership
RUN chown cloudsync:cloudsync app.jar

USER cloudsync

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/actuator/health || exit 1

EXPOSE 8080

ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
