# ---------------------------------------------------------------------------
# One Dockerfile for every Spring application in IT-ILMS.
#
# It packages a jar that Maven has already built; it does not run Maven itself.
#
#   mvn -DskipTests package                      # from the repository root
#   docker build -f ../../deploy/docker/Dockerfile services/identity-service
#
# (docker compose does the second step for every service.)
#
# Why the jar is built outside the image: the same jar that passed the test
# suite is the one that ships. In CI the pipeline already runs
# "mvn verify", so compiling a second time inside Docker would only add
# minutes and a chance of the two builds differing. Locally it avoids pulling
# a large Maven image and re-downloading every dependency into it.
#
# The build context is the module's own directory, so each image sees only its
# own target/ folder.
# ---------------------------------------------------------------------------

FROM eclipse-temurin:21-jre-alpine

# Never run as root. A remote-code-execution bug in a dependency should land
# an attacker in an unprivileged account, not in control of the container.
RUN addgroup -S itilms && adduser -S -G itilms itilms \
 && mkdir -p /app/storage \
 && chown -R itilms:itilms /app

WORKDIR /app

# Spring Boot's repackaged jar. The plain "*.jar.original" does not match.
# If this step fails with "no source files", run mvn package first.
COPY --chown=itilms:itilms target/*.jar app.jar

USER itilms

# Size the heap from the container's memory limit rather than the host's RAM,
# and exit on OOM so the orchestrator restarts a healthy process instead of
# leaving a thrashing one serving errors.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
