# Multi-stage build: stage 1 compiles, stage 2 is the runtime image.
# The builder stage uses a full JDK image (~500MB). The runtime stage uses
# a slim JRE image (~200MB). The final image never contains Maven, source
# code, or test dependencies — only the compiled JAR and a JRE.

# ── Stage 1: build ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

# Copy dependency descriptors first so Docker can cache the dependency
# download layer separately from the source layer. If only src/ changes,
# the `mvn dependency:go-offline` layer is reused from cache.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline -B

COPY src/ src/
RUN ./mvnw -q package -DskipTests -B

# ── Stage 2: runtime ──────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Run as non-root — a container running as root has write access to the
# entire container filesystem, which amplifies the impact of any exploit.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
