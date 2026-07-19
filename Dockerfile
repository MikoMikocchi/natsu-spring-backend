# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage
# ---------------------------------------------------------------------------
# NOTE on base image tag: pom.xml pins <java.version>26</java.version>, and CI
# (.github/workflows/ci.yml) already builds/tests on JDK 26 via
# actions/setup-java@v4 (distribution: temurin). At the time this Dockerfile
# was written, `eclipse-temurin:26-jdk` and `eclipse-temurin:26-jre` were
# confirmed to exist and pull successfully (multi-arch, amd64 + arm64). Java 26
# is very recent, so re-verify these tags still resolve before relying on this
# in a real deploy -- if Temurin ever stops publishing a matching `26-*` tag,
# pin to a specific eclipse-temurin digest or drop back to the last LTS you can
# realistically ship on.
FROM eclipse-temurin:26-jdk AS build

WORKDIR /workspace

# Copy only the files needed to resolve dependencies first, so this layer
# (the slow part -- downloading half the internet from Maven Central) stays
# cached across builds where only application source changed.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Now bring in the actual source and build the fat jar. Tests are deliberately
# skipped here -- they already run in CI (including a Testcontainers suite
# that needs a real Docker socket), and re-running them inside the image build
# would mean Docker-in-Docker, which is unnecessary and fragile for what is
# just a packaging step.
COPY src/ src/
RUN ./mvnw -B package -DskipTests

# ---------------------------------------------------------------------------
# Runtime stage
# ---------------------------------------------------------------------------
# JRE-only image: no compiler, no jlink/jmod tooling, meaningfully smaller
# than the JDK image used to build. We don't need anything else at runtime.
FROM eclipse-temurin:26-jre AS runtime

# Split the fat jar into layers (dependencies / spring-boot-loader /
# snapshot-dependencies / application) using Spring Boot's layertools, which
# spring-boot-maven-plugin enables by default (no extra pom.xml config
# needed). This means the (large, rarely-changing) third-party dependency
# layer gets its own Docker layer, separate from our own (small, frequently
# changing) application classes -- so a typical code change only invalidates
# a small layer instead of the whole jar, and pulls/pushes/re-deploys stay
# cheap. Worth doing here: it's a few extra RUN lines for a real, ongoing
# caching benefit, not speculative complexity.
COPY --from=build /workspace/target/*.jar /tmp/app.jar
WORKDIR /tmp
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination /app

FROM eclipse-temurin:26-jre AS final

# Pandoc powers DOCX/RTF import (PandocBridgeImporter shells out to the `pandoc` CLI to convert
# to EPUB before handing off to EpubImporter). Installed from the base image's own apt repos
# rather than pinning a specific Pandoc version -- docx/rtf->epub conversion has been stable
# across Pandoc releases for years, so whatever Debian ships alongside this Temurin base is fine.
RUN apt-get update && apt-get install -y --no-install-recommends curl pandoc && rm -rf /var/lib/apt/lists/*

# Dedicated non-root user to run the app -- standard container hardening,
# avoids running application code as root inside the container. UID/GID
# 10001 is used instead of 1000 because the eclipse-temurin base image
# already ships a "ubuntu" user/group at 1000.
RUN groupadd --system --gid 10001 natsu && \
    useradd --system --uid 10001 --gid natsu --no-create-home --shell /usr/sbin/nologin natsu

WORKDIR /app

COPY --from=runtime --chown=natsu:natsu /app/dependencies/ ./
COPY --from=runtime --chown=natsu:natsu /app/spring-boot-loader/ ./
COPY --from=runtime --chown=natsu:natsu /app/snapshot-dependencies/ ./
COPY --from=runtime --chown=natsu:natsu /app/application/ ./

USER natsu:natsu

# Matches server.port in src/main/resources/application.yml (this app does
# not use the Spring Boot default of 8080).
EXPOSE 3000

# Readiness (not raw TCP) confirms Spring context is up and Postgres is reachable.
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s \
  CMD curl -fsS http://localhost:3000/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
