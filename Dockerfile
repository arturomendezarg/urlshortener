# Shared multi-stage Dockerfile for every Spring Boot module in this Maven reactor
# (v1-legacy-monolith, api-gateway, v2-shortener-service, analytics-worker, bulk-processor).
#
# One Dockerfile instead of five near-identical ones: all five modules build the same way (the
# same reactor, "mvn -pl <module> -am package -DskipTests", then a fat jar copied into a slim
# runtime image) and the only thing that differs between them is which module's jar ends up in
# the final image. That difference is expressed as a single build arg (MODULE) rather than
# duplicated Dockerfile boilerplate five times over. The trade-off, made deliberately given this
# exercise's timebox: this always copies the WHOLE reactor's sources into the build stage and
# lets "-am" (also-make) figure out which of the other modules actually need to be compiled as
# dependencies, rather than a curated per-module COPY list -- simpler and harder to get subtly
# wrong than hand-maintaining which module needs which siblings, at the cost of a slightly larger
# build context and no Docker layer caching of the Maven dependency-download step (a production
# Dockerfile would COPY the poms first, run "dependency:go-offline" in its own layer, and only
# then COPY src -- skipped here since this is built once per kind demo, not on a tight CI loop).
#
# Build (from the repo root, so the reactor's own pom.xml and every module directory are in the
# build context -- see infra/k8s/deploy-to-kind.sh, which does exactly this for all 5 modules):
#   docker build --build-arg MODULE=v2-shortener-service -t v2-shortener-service:kind .
ARG MODULE

FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE
WORKDIR /workspace

# Whole reactor: the parent pom plus every module's pom.xml and src/, since any of the app
# modules can depend on v2-shortener-contract and "-am" needs its sources to build it too.
COPY pom.xml .
COPY v1-legacy-monolith v1-legacy-monolith
COPY api-gateway api-gateway
COPY v2-shortener-contract v2-shortener-contract
COPY analytics-worker analytics-worker
COPY v2-shortener-service v2-shortener-service
COPY bulk-processor bulk-processor

# -am (also-make): builds MODULE plus whichever of its own reactor dependencies it needs (e.g.
# v2-shortener-contract for the V2 services), instead of rebuilding the entire reactor for every
# image. -DskipTests: this stage's job is packaging a jar from code this project's own CI
# (mvn verify, ARCHITECTURE.md section 11) already tested on every PR -- re-running the full
# suite (including Testcontainers-backed integration tests) again per "docker build" would be
# redundant and considerably slower for zero extra signal.
RUN mvn -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine
ARG MODULE

# Runs as a non-root user -- standard container hardening with no real downside here. Created
# before WORKDIR/COPY so the copied jar can be chowned to it directly, rather than switching USER
# first and hitting a permission-denied writing into a root-owned /app.
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app
# The wildcard is safe against spring-boot-maven-plugin's own output: it renames the plain
# (pre-repackage) jar to "*.jar.original" alongside the fat jar it repackages under the normal
# name, and ".jar.original" does not match "*.jar".
COPY --from=build --chown=spring:spring /workspace/${MODULE}/target/*.jar app.jar

USER spring:spring
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
